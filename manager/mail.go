package main

import (
	"bufio"
	"bytes"
	"crypto/tls"
	"encoding/base64"
	"fmt"
	"io"
	"mime"
	"mime/multipart"
	"mime/quotedprintable"
	"net/mail"
	"net/smtp"
	"os"
	"regexp"
	"sort"
	"strconv"
	"strings"
	"time"
)

type imapConn struct {
	c   *tls.Conn
	r   *bufio.Reader
	w   *bufio.Writer
	tag int
}

func dialIMAP(email, password string) (*imapConn, error) {
	c, err := tls.Dial("tcp", "imap.mail.yahoo.com:993", &tls.Config{ServerName: "imap.mail.yahoo.com", MinVersion: tls.VersionTLS12})
	if err != nil {
		return nil, err
	}
	ic := &imapConn{c: c, r: bufio.NewReaderSize(c, 64*1024), w: bufio.NewWriterSize(c, 16*1024)}
	if _, err := ic.r.ReadString('\n'); err != nil {
		c.Close()
		return nil, err
	}
	if _, err := ic.command(`LOGIN %s %s`, imapQuote(email), imapQuote(password)); err != nil {
		c.Close()
		return nil, err
	}
	if _, err := ic.command(`SELECT "INBOX"`); err != nil {
		c.Close()
		return nil, err
	}
	return ic, nil
}

func (c *imapConn) close() {
	if c != nil && c.c != nil {
		_, _ = c.command("LOGOUT")
		_ = c.c.Close()
	}
}

func imapQuote(s string) string {
	return `"` + strings.ReplaceAll(strings.ReplaceAll(s, `\`, `\\`), `"`, `\"`) + `"`
}

func (c *imapConn) command(format string, args ...any) ([]string, error) {
	c.tag++
	tag := fmt.Sprintf("A%04d", c.tag)
	if _, err := fmt.Fprintf(c.w, "%s %s\r\n", tag, fmt.Sprintf(format, args...)); err != nil {
		return nil, err
	}
	if err := c.w.Flush(); err != nil {
		return nil, err
	}
	var lines []string
	for {
		line, err := c.r.ReadString('\n')
		if err != nil {
			return lines, err
		}
		line = strings.TrimRight(line, "\r\n")
		lines = append(lines, line)
		if strings.HasPrefix(line, tag+" ") {
			if strings.Contains(strings.ToUpper(line), " OK") || strings.HasPrefix(strings.ToUpper(line), tag+" OK") {
				return lines, nil
			}
			return lines, fmt.Errorf("IMAP command failed: %s", line)
		}
	}
}

func (c *imapConn) searchSince(days int) ([]int, error) {
	since := time.Now().AddDate(0, 0, -days).Format("02-Jan-2006")
	lines, err := c.command("SEARCH SINCE %s", since)
	if err != nil {
		return nil, err
	}
	var ids []int
	for _, line := range lines {
		if strings.HasPrefix(line, "* SEARCH") {
			for _, p := range strings.Fields(strings.TrimPrefix(line, "* SEARCH")) {
				if n, e := strconv.Atoi(p); e == nil {
					ids = append(ids, n)
				}
			}
		}
	}
	sort.Ints(ids)
	if len(ids) > 100 {
		ids = ids[len(ids)-100:]
	}
	return ids, nil
}

var literalRe = regexp.MustCompile(`\{(\d+)\}$`)

func (c *imapConn) fetchRaw(seq int) ([]byte, error) {
	c.tag++
	tag := fmt.Sprintf("A%04d", c.tag)
	if _, err := fmt.Fprintf(c.w, "%s FETCH %d BODY.PEEK[]\r\n", tag, seq); err != nil {
		return nil, err
	}
	if err := c.w.Flush(); err != nil {
		return nil, err
	}
	var raw []byte
	for {
		line, err := c.r.ReadString('\n')
		if err != nil {
			return nil, err
		}
		clean := strings.TrimRight(line, "\r\n")
		if m := literalRe.FindStringSubmatch(clean); len(m) == 2 {
			n, _ := strconv.Atoi(m[1])
			raw = make([]byte, n)
			if _, err := io.ReadFull(c.r, raw); err != nil {
				return nil, err
			}
			// Consume CRLF following literal if present.
			_, _ = c.r.ReadString('\n')
		}
		if strings.HasPrefix(clean, tag+" ") {
			if strings.Contains(strings.ToUpper(clean), "OK") {
				return raw, nil
			}
			return nil, fmt.Errorf("IMAP fetch failed: %s", clean)
		}
	}
}

func fetchRecentJobAlertMails(email, password string, seen map[string]bool) ([]MailLead, error) {
	ic, err := dialIMAP(email, password)
	if err != nil {
		return nil, err
	}
	defer ic.close()
	ids, err := ic.searchSince(7)
	if err != nil {
		return nil, err
	}
	leads := make([]MailLead, 0)
	for _, seq := range ids {
		raw, err := ic.fetchRaw(seq)
		if err != nil {
			continue
		}
		if len(raw) == 0 {
			continue
		}
		lead, err := parseMailLead(raw)
		if err != nil {
			continue
		}
		if lead.MessageID == "" {
			lead.MessageID = fmt.Sprintf("seq-%d", seq)
		}
		if seen[lead.MessageID] {
			continue
		}
		if !looksLikeJobAlert(lead) {
			continue
		}
		leads = append(leads, lead)
	}
	return leads, nil
}

func parseMailLead(raw []byte) (MailLead, error) {
	msg, err := mail.ReadMessage(bytes.NewReader(raw))
	if err != nil {
		return MailLead{}, err
	}
	wd := new(mime.WordDecoder)
	subject, _ := wd.DecodeHeader(msg.Header.Get("Subject"))
	from, _ := wd.DecodeHeader(msg.Header.Get("From"))
	ctype := msg.Header.Get("Content-Type")
	text, htmlBody, err := readMIMEBody(msg.Body, ctype, msg.Header.Get("Content-Transfer-Encoding"))
	if err != nil {
		return MailLead{}, err
	}
	links := extractLinks(text, htmlBody)
	return MailLead{MessageID: strings.TrimSpace(msg.Header.Get("Message-ID")), Subject: subject, From: from, Text: cleanText(text), HTML: htmlBody, Links: links}, nil
}

func readMIMEBody(r io.Reader, contentType, transfer string) (string, string, error) {
	mt, params, err := mime.ParseMediaType(contentType)
	if err != nil || mt == "" {
		mt = "text/plain"
	}
	if strings.HasPrefix(mt, "multipart/") {
		mr := multipart.NewReader(r, params["boundary"])
		var texts, htmls []string
		for {
			p, err := mr.NextPart()
			if err == io.EOF {
				break
			}
			if err != nil {
				return "", "", err
			}
			b, err := readAllDecoded(p, p.Header.Get("Content-Transfer-Encoding"))
			if err != nil {
				continue
			}
			t, h, _ := readMIMEBody(bytes.NewReader(b), p.Header.Get("Content-Type"), "")
			if t != "" {
				texts = append(texts, t)
			}
			if h != "" {
				htmls = append(htmls, h)
			}
		}
		return strings.Join(texts, "\n"), strings.Join(htmls, "\n"), nil
	}
	b, err := readAllDecodedReader(r, transfer)
	if err != nil {
		return "", "", err
	}
	s := string(b)
	if strings.HasPrefix(mt, "text/html") {
		return stripHTML(s), s, nil
	}
	if strings.HasPrefix(mt, "text/plain") {
		return s, "", nil
	}
	return "", "", nil
}

func readAllDecoded(p *multipart.Part, transfer string) ([]byte, error) {
	return readAllDecodedReader(p, transfer)
}
func readAllDecodedReader(r io.Reader, transfer string) ([]byte, error) {
	switch strings.ToLower(strings.TrimSpace(transfer)) {
	case "base64":
		return io.ReadAll(base64.NewDecoder(base64.StdEncoding, r))
	case "quoted-printable":
		return io.ReadAll(quotedprintable.NewReader(r))
	default:
		return io.ReadAll(io.LimitReader(r, 5<<20))
	}
}

var hrefRe = regexp.MustCompile(`(?i)href\s*=\s*["'](https?://[^"']+)["']`)
var urlRe = regexp.MustCompile(`https?://[^\s<>"')\]]+`)

func extractLinks(text, htmlBody string) []string {
	set := map[string]bool{}
	var out []string
	for _, m := range hrefRe.FindAllStringSubmatch(htmlBody, -1) {
		if len(m) > 1 {
			set[m[1]] = true
		}
	}
	for _, u := range urlRe.FindAllString(text, -1) {
		set[strings.TrimRight(u, ".,;!?")] = true
	}
	for u := range set {
		l := strings.ToLower(u)
		if strings.Contains(l, "unsubscribe") || strings.Contains(l, "privacy") || strings.Contains(l, "preferences") || strings.Contains(l, "tracking") {
			continue
		}
		out = append(out, u)
	}
	sort.Strings(out)
	if len(out) > 20 {
		out = out[:20]
	}
	return out
}

func looksLikeJobAlert(l MailLead) bool {
	x := strings.ToLower(l.Subject + " " + l.From + " " + strings.Join(l.Links, " "))
	terms := []string{"job", "vacancy", "career", "position", "opportunity", "linkedin", "profession.hu", "zyntern", "eures", "corvinus", "indeed", "workday", "greenhouse", "lever.co", "smartrecruiters"}
	for _, t := range terms {
		if strings.Contains(x, t) {
			return true
		}
	}
	return false
}

func sendApplicationMail(cfg Config, password string, p CandidateProfile, job Job, cover string) (string, error) {
	host := "smtp.mail.yahoo.com"
	conn, err := tls.Dial("tcp", host+":465", &tls.Config{ServerName: host, MinVersion: tls.VersionTLS12})
	if err != nil {
		return "", err
	}
	defer conn.Close()
	client, err := smtp.NewClient(conn, host)
	if err != nil {
		return "", err
	}
	defer client.Close()
	auth := smtp.PlainAuth("", cfg.Email, password, host)
	if err := client.Auth(auth); err != nil {
		return "", err
	}
	if err := client.Mail(cfg.Email); err != nil {
		return "", err
	}
	if err := client.Rcpt(job.ApplicationEmail); err != nil {
		return "", err
	}
	wc, err := client.Data()
	if err != nil {
		return "", err
	}
	boundary := "HAYA-" + time.Now().Format("20060102150405")
	var b bytes.Buffer
	fmt.Fprintf(&b, "From: %s <%s>\r\nTo: %s\r\nSubject: %s\r\nMIME-Version: 1.0\r\nContent-Type: multipart/mixed; boundary=%q\r\n\r\n", p.Name, cfg.Email, job.ApplicationEmail, mime.QEncoding.Encode("utf-8", "Application - "+job.Title+" - "+p.Name), boundary)
	fmt.Fprintf(&b, "--%s\r\nContent-Type: text/plain; charset=utf-8\r\nContent-Transfer-Encoding: quoted-printable\r\n\r\n", boundary)
	qp := quotedprintable.NewWriter(&b)
	_, _ = qp.Write([]byte(cover))
	_ = qp.Close()
	b.WriteString("\r\n")
	cv, err := os.ReadFile(cvPath())
	if err != nil {
		return "", err
	}
	fmt.Fprintf(&b, "--%s\r\nContent-Type: application/pdf; name=\"Haya_Saadeh_CV.pdf\"\r\nContent-Disposition: attachment; filename=\"Haya_Saadeh_CV.pdf\"\r\nContent-Transfer-Encoding: base64\r\n\r\n", boundary)
	enc := base64.StdEncoding.EncodeToString(cv)
	for len(enc) > 76 {
		b.WriteString(enc[:76] + "\r\n")
		enc = enc[76:]
	}
	if enc != "" {
		b.WriteString(enc + "\r\n")
	}
	fmt.Fprintf(&b, "--%s--\r\n", boundary)
	if _, err := wc.Write(b.Bytes()); err != nil {
		wc.Close()
		return "", err
	}
	if err := wc.Close(); err != nil {
		return "", err
	}
	_ = client.Quit()
	return "smtp-" + time.Now().UTC().Format("20060102T150405Z"), nil
}
