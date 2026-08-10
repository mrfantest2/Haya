package main

import (
	"context"
	"crypto/sha256"
	"encoding/hex"
	"errors"
	"fmt"
	"html"
	"io"
	"net/http"
	"net/url"
	"os"
	"path/filepath"
	"regexp"
	"sort"
	"strconv"
	"strings"
	"sync"
	"time"
)

var agentRunMu sync.Mutex
var agentRunning bool

func runAgentMode() {
	mutex, already, err := createNamedMutex("Local\\HayaJobAutopilotAgentV3")
	if err != nil {
		return
	}
	defer closeHandle(mutex)
	if already {
		return
	}
	if err := ensureInstalledRuntime(); err != nil {
		appendLog("Agent startup failed", map[string]any{"error": err.Error()})
		return
	}

	state, _ := loadState()
	state.AgentPID = os.Getpid()
	_ = saveState(state)
	appendLog("Agent started", map[string]any{"version": appVersion, "pid": os.Getpid()})

	serverDone := make(chan struct{})
	go func() { _ = runLocalServer(); close(serverDone) }()

	cfg, _ := loadConfig()
	interval := time.Duration(cfg.ScanIntervalHours) * time.Hour
	if interval < time.Hour {
		interval = 6 * time.Hour
	}
	timer := time.NewTimer(25 * time.Second)
	ticker := time.NewTicker(interval)
	defer timer.Stop()
	defer ticker.Stop()

	for {
		select {
		case <-timer.C:
			go runAgentOnce("startup")
		case <-ticker.C:
			go runAgentOnce("scheduled")
		case <-serverDone:
			return
		}
	}
}

func ensureInstalledRuntime() error {
	if err := ensureDirs(); err != nil {
		return err
	}
	if _, err := os.Stat(cvPath()); errors.Is(err, os.ErrNotExist) {
		if err := os.WriteFile(cvPath(), embeddedCV, 0600); err != nil {
			return err
		}
	}
	if _, err := os.Stat(configPath()); errors.Is(err, os.ErrNotExist) {
		if err := saveConfig(defaultConfig()); err != nil {
			return err
		}
	}
	if _, err := os.Stat(jobsPath()); errors.Is(err, os.ErrNotExist) {
		if err := saveJobs([]Job{}); err != nil {
			return err
		}
	}
	if _, err := os.Stat(statePath()); errors.Is(err, os.ErrNotExist) {
		if err := saveState(State{ApplicationsByDay: map[string]int{}}); err != nil {
			return err
		}
	}
	return nil
}

func runAgentOnce(reason string) (result map[string]any) {
	agentRunMu.Lock()
	if agentRunning {
		agentRunMu.Unlock()
		return map[string]any{"ok": false, "error": "Agent is already running"}
	}
	agentRunning = true
	agentRunMu.Unlock()
	defer func() { agentRunMu.Lock(); agentRunning = false; agentRunMu.Unlock() }()

	result = map[string]any{"ok": false, "newJobs": 0}
	cfg, err := loadConfig()
	if err != nil {
		result["error"] = err.Error()
		return
	}
	password, err := unprotectString(cfg.EncryptedMailAppPassword)
	if err != nil {
		result["error"] = "Yahoo credential could not be decrypted"
		return
	}
	if password == "" {
		result["error"] = "Yahoo app password is not configured"
		updateRunState(reason, result["error"].(string))
		return
	}
	profile, err := loadProfile()
	if err != nil {
		result["error"] = err.Error()
		return
	}
	state, _ := loadState()
	jobs, _ := loadJobs()
	seen := map[string]bool{}
	for _, id := range state.SeenMessageIDs {
		seen[id] = true
	}
	leads, err := fetchRecentJobAlertMails(cfg.Email, password, seen)
	if err != nil {
		result["error"] = "Mailbox check failed: " + err.Error()
		updateRunState(reason, result["error"].(string))
		appendLog("Run failed", map[string]any{"reason": reason, "error": result["error"]})
		return
	}

	knownURLs := map[string]bool{}
	for _, j := range jobs {
		if j.URL != "" {
			knownURLs[j.URL] = true
		}
	}
	var added []Job
	for _, lead := range leads {
		seen[lead.MessageID] = true
		for _, link := range lead.Links {
			if knownURLs[link] {
				continue
			}
			page, err := fetchPage(link)
			if err != nil {
				// Job-alert emails often contain enough vacancy text even when a platform
				// blocks unauthenticated page retrieval. Track it for review, but strict
				// auto-apply still requires a verified corporate application route.
				page = PageData{URL: link, Title: lead.Subject, Text: lead.Text, HTML: lead.HTML}
			}
			job := analyzeJob(page, lead, profile, cfg)
			if job.ID == "" {
				continue
			}
			if knownURLs[job.URL] {
				continue
			}
			knownURLs[job.URL] = true
			_ = writePreparedApplication(job, profile, cfg)
			blocks := autoSubmissionBlocks(job, cfg, password)
			job.AutoSubmitBlocks = blocks
			if strings.EqualFold(cfg.AutomationMode, "Strict Auto Apply") && len(blocks) == 0 {
				day := budapestDay(time.Now())
				count := state.ApplicationsByDay[day]
				if count >= cfg.DailyApplicationLimit {
					job.Status = "Queued - daily limit reached"
					job.AutoSubmitBlocks = append(job.AutoSubmitBlocks, "Daily application limit reached")
				} else {
					cover := coverLetter(job, profile, cfg)
					id, sendErr := sendApplicationMail(cfg, password, profile, job, cover)
					if sendErr != nil {
						job.Status = "Needs Haya"
						job.AutoSubmitBlocks = append(job.AutoSubmitBlocks, "Automatic email failed: "+sendErr.Error())
					} else {
						now := time.Now()
						job.Status = "Applied automatically"
						job.AppliedAt = &now
						job.SMTPMessageID = id
						state.ApplicationsByDay[day] = count + 1
					}
				}
			} else if job.Score >= cfg.MinimumMatchScore && job.Risk <= cfg.MaximumFraudRisk {
				job.Status = "Prepared - review required"
			} else {
				job.Status = "Filtered"
			}
			added = append(added, job)
		}
	}
	state.SeenMessageIDs = mapKeysSortedLimited(seen, 1200)
	now := time.Now()
	state.LastRun = &now
	state.LastError = ""
	state.LastRunReason = reason
	merged := append(added, jobs...)
	if len(merged) > 2500 {
		merged = merged[:2500]
	}
	_ = saveJobs(merged)
	_ = saveState(state)
	appendLog("Run complete", map[string]any{"reason": reason, "newJobs": len(added), "messages": len(leads)})
	result["ok"] = true
	result["newJobs"] = len(added)
	return
}

func updateRunState(reason, msg string) {
	s, _ := loadState()
	now := time.Now()
	s.LastRun = &now
	s.LastError = msg
	s.LastRunReason = reason
	_ = saveState(s)
}

func fetchPage(rawURL string) (PageData, error) {
	u, err := url.Parse(rawURL)
	if err != nil || !(u.Scheme == "http" || u.Scheme == "https") {
		return PageData{}, errors.New("invalid URL")
	}
	ctx, cancel := context.WithTimeout(context.Background(), 15*time.Second)
	defer cancel()
	req, _ := http.NewRequestWithContext(ctx, http.MethodGet, rawURL, nil)
	req.Header.Set("User-Agent", "Mozilla/5.0 (compatible; HayaJobAutopilot/3.0; private-assistant)")
	req.Header.Set("Accept", "text/html,application/xhtml+xml")
	client := &http.Client{Timeout: 15 * time.Second, CheckRedirect: func(req *http.Request, via []*http.Request) error {
		if len(via) > 8 {
			return errors.New("too many redirects")
		}
		return nil
	}}
	resp, err := client.Do(req)
	if err != nil {
		return PageData{}, err
	}
	defer resp.Body.Close()
	if resp.StatusCode < 200 || resp.StatusCode >= 400 {
		return PageData{}, fmt.Errorf("HTTP %d", resp.StatusCode)
	}
	b, err := io.ReadAll(io.LimitReader(resp.Body, 2<<20))
	if err != nil {
		return PageData{}, err
	}
	htmlBody := string(b)
	title := extractHTMLTitle(htmlBody)
	text := stripHTML(htmlBody)
	return PageData{URL: resp.Request.URL.String(), Title: title, Text: cleanText(text), HTML: htmlBody}, nil
}

var titleRe = regexp.MustCompile(`(?is)<title[^>]*>(.*?)</title>`)
var tagsRe = regexp.MustCompile(`(?is)<script[^>]*>.*?</script>|<style[^>]*>.*?</style>|<[^>]+>`)

func extractHTMLTitle(s string) string {
	m := titleRe.FindStringSubmatch(s)
	if len(m) > 1 {
		return cleanText(html.UnescapeString(m[1]))
	}
	return ""
}
func stripHTML(s string) string {
	return cleanText(html.UnescapeString(tagsRe.ReplaceAllString(s, " ")))
}
func cleanText(s string) string {
	return strings.Join(strings.Fields(strings.ReplaceAll(s, "\x00", " ")), " ")
}

func analyzeJob(page PageData, lead MailLead, p CandidateProfile, cfg Config) Job {
	combined := cleanText(lead.Subject + " " + page.Title + " " + page.Text)
	title := extractJobTitle(page.Title, lead.Subject)
	company := extractCompany(page.Title, lead.Subject)
	score, track, reasons, concerns := scoreJob(combined, p, cfg)
	risk, riskSignals := riskScore(combined)
	applicationEmail := selectApplicationEmail(extractEmails(combined), page.URL)
	id := jobID(page.URL, title, company)
	decision := "Low priority"
	if score >= 85 {
		decision = "Excellent match"
	} else if score >= 75 {
		decision = "Strong match"
	} else if score >= 65 {
		decision = "Manual review"
	}
	return Job{ID: id, Title: title, Company: company, URL: page.URL, Source: lead.From, FoundAt: time.Now(), Score: score, Risk: risk, Track: track, Decision: decision, Status: "New", ApplicationEmail: applicationEmail, Reasons: reasons, Concerns: concerns, RiskSignals: riskSignals, SourceMessageID: lead.MessageID, RawText: combined}
}

func scoreJob(text string, p CandidateProfile, cfg Config) (int, string, []string, []string) {
	x := strings.ToLower(text)
	score := 0
	track := "General"
	var reasons, concerns []string
	tracks := map[string][]string{"Operations & Training": {"training coordinator", "training administrator", "operations coordinator", "programme coordinator", "program coordinator", "learning and development"}, "Recruitment & People": {"recruitment coordinator", "talent acquisition", "hr coordinator", "people operations", "onboarding coordinator"}, "Aviation & Travel": {"airline", "aviation", "travel consultant", "reservations", "ticketing", "airport services"}, "Healthcare": {"patient care", "patient experience", "healthcare operations", "international patient"}, "University": {"international student", "admissions", "student services", "programme assistant", "program assistant"}}
	best := 0
	for name, terms := range tracks {
		hits := 0
		for _, t := range terms {
			if strings.Contains(x, t) {
				hits++
			}
		}
		if hits > best {
			best = hits
			track = name
		}
	}
	if best > 0 {
		score += 25
		reasons = append(reasons, "Target role family: "+track)
	}
	keys := []string{"training", "recruit", "onboard", "operations", "coordinator", "programme", "program", "report", "schedule", "stakeholder", "aviation", "travel", "patient", "customer", "documentation", "process improvement"}
	hits := 0
	for _, k := range keys {
		if strings.Contains(x, k) {
			hits++
		}
	}
	overlap := hits * 2
	if overlap > 20 {
		overlap = 20
	}
	score += overlap
	if overlap >= 10 {
		reasons = append(reasons, "Strong overlap with Haya's experience")
	}
	if regexp.MustCompile(`(?i)\benglish\b|english-speaking|fluent english`).MatchString(text) {
		score += 15
		reasons = append(reasons, "English-language role")
	} else {
		concerns = append(concerns, "English requirement not clear")
	}
	if regexp.MustCompile(`(?i)budapest|hybrid.{0,30}hungary|hungary.{0,30}hybrid`).MatchString(text) {
		score += 10
		reasons = append(reasons, "Budapest / Hungary hybrid")
	} else {
		concerns = append(concerns, "Budapest location not confirmed")
	}
	if regexp.MustCompile(`(?i)part[- ]time|student job|intern(ship)?|részmunkaidő|gyakornok|20 hours|25 hours|30 hours`).MatchString(text) {
		score += 10
		reasons = append(reasons, "Student-compatible schedule")
	} else {
		concerns = append(concerns, "Weekly hours not clear")
	}
	if regexp.MustCompile(`(?i)coordina|administration|schedule|reporting|documentation|stakeholder`).MatchString(text) {
		score += 10
		reasons = append(reasons, "Coordination and administration duties")
	}
	if strings.Contains(x, "arabic") {
		score += 5
		reasons = append(reasons, "Arabic language advantage")
	}
	if regexp.MustCompile(`(?i)hungarian.{0,20}(b2|c1|c2|fluent|native)|native hungarian|fluent hungarian`).MatchString(text) {
		score -= 45
		concerns = append(concerns, "Advanced Hungarian appears mandatory")
	}
	if regexp.MustCompile(`(?i)unpaid|commission[- ]only|application fee|processing fee|pay.{0,20}training`).MatchString(text) {
		score -= 70
		concerns = append(concerns, "Unsafe or unsuitable compensation/application term")
	}
	for _, c := range cfg.ExcludedCompanies {
		if c != "" && strings.Contains(x, strings.ToLower(c)) {
			score -= 100
			concerns = append(concerns, "Excluded company")
		}
	}
	if score < 0 {
		score = 0
	}
	if score > 100 {
		score = 100
	}
	return score, track, reasons, concerns
}

func riskScore(text string) (int, []string) {
	x := strings.ToLower(text)
	risk := 0
	var sig []string
	add := func(n int, s string) { risk += n; sig = append(sig, s) }
	if regexp.MustCompile(`application fee|processing fee|send money|pay.{0,20}before`).MatchString(x) {
		add(70, "Applicant payment request")
	}
	if regexp.MustCompile(`crypto|bitcoin|gift card|western union|moneygram`).MatchString(x) {
		add(65, "Unusual payment method")
	}
	if regexp.MustCompile(`bank account|online banking|credit card`).MatchString(x) {
		add(40, "Banking information request")
	}
	if regexp.MustCompile(`telegram|whatsapp only`).MatchString(x) {
		add(20, "Messaging-app-only recruitment")
	}
	if regexp.MustCompile(`no interview|guaranteed job|start today`).MatchString(x) {
		add(25, "Unrealistic hiring process")
	}
	if risk > 100 {
		risk = 100
	}
	return risk, sig
}

func extractJobTitle(pageTitle, subject string) string {
	v := cleanText(pageTitle)
	if v == "" {
		v = cleanText(subject)
	}
	for _, sep := range []string{" | ", " - ", " – ", " — "} {
		if i := strings.Index(v, sep); i > 0 {
			v = v[:i]
			break
		}
	}
	if len(v) > 180 {
		v = v[:180]
	}
	if v == "" {
		v = "Opportunity"
	}
	return v
}
func extractCompany(pageTitle, subject string) string {
	v := cleanText(pageTitle)
	if v == "" {
		v = cleanText(subject)
	}
	for _, sep := range []string{" at ", " | ", " - ", " – ", " — "} {
		parts := strings.Split(v, sep)
		if len(parts) >= 2 {
			c := strings.TrimSpace(parts[len(parts)-1])
			if len(c) > 120 {
				c = c[:120]
			}
			if c != "" {
				return c
			}
		}
	}
	return "Company not confidently extracted"
}

var emailRe = regexp.MustCompile(`(?i)[A-Z0-9._%+\-]+@[A-Z0-9.\-]+\.[A-Z]{2,}`)

func extractEmails(text string) []string {
	set := map[string]bool{}
	for _, e := range emailRe.FindAllString(text, -1) {
		set[strings.ToLower(e)] = true
	}
	var out []string
	for e := range set {
		out = append(out, e)
	}
	sort.Strings(out)
	return out
}
func selectApplicationEmail(emails []string, rawURL string) string {
	keywords := []string{"career", "careers", "job", "jobs", "recruit", "talent", "hr", "hiring", "application", "apply"}
	for _, emailAddr := range emails {
		if !corporateEmailForURL(emailAddr, rawURL) {
			continue
		}
		local := strings.ToLower(strings.SplitN(emailAddr, "@", 2)[0])
		for _, keyword := range keywords {
			if strings.Contains(local, keyword) {
				return emailAddr
			}
		}
	}
	return ""
}

func corporateEmailForURL(emailAddr, rawURL string) bool {
	parts := strings.Split(emailAddr, "@")
	if len(parts) != 2 {
		return false
	}
	d := strings.ToLower(parts[1])
	free := map[string]bool{"gmail.com": true, "yahoo.com": true, "outlook.com": true, "hotmail.com": true, "icloud.com": true, "proton.me": true}
	if free[d] {
		return false
	}
	u, err := url.Parse(rawURL)
	if err != nil {
		return false
	}
	h := strings.TrimPrefix(strings.ToLower(u.Hostname()), "www.")
	return h == d || strings.HasSuffix(h, "."+d) || strings.HasSuffix(d, "."+h)
}
func jobID(url, title, company string) string {
	sum := sha256.Sum256([]byte(url + "|" + title + "|" + company))
	return hex.EncodeToString(sum[:])[:20]
}

func autoSubmissionBlocks(job Job, cfg Config, password string) []string {
	var b []string
	if cfg.AutomationMode != "Strict Auto Apply" {
		b = append(b, "Strict Auto Apply is not enabled")
	}
	if password == "" {
		b = append(b, "Yahoo app password missing")
	}
	if cfg.WorkAuthorizationHungary == "" {
		b = append(b, "Work authorization answer missing")
	}
	if cfg.SponsorshipRequired == "" {
		b = append(b, "Sponsorship answer missing")
	}
	if cfg.EarliestStartDate == "" {
		b = append(b, "Earliest start date missing")
	}
	if _, e := os.Stat(cvPath()); e != nil {
		b = append(b, "CV missing")
	}
	if job.Score < 85 {
		b = append(b, "Match score below 85% strict threshold")
	}
	if job.Risk > cfg.MaximumFraudRisk {
		b = append(b, "Fraud risk above configured limit")
	}
	if job.ApplicationEmail == "" || !corporateEmailForURL(job.ApplicationEmail, job.URL) {
		b = append(b, "No verified corporate application email")
	}
	return b
}

func coverLetter(job Job, p CandidateProfile, cfg Config) string {
	return fmt.Sprintf("Dear Hiring Team,\n\nI am applying for the %s opportunity at %s. I am currently completing a full-time MBA at Corvinus University of Budapest and bring more than seven years of international experience across airline and multinational operations, recruitment coordination, training administration, onboarding, reporting, customer service, travel and healthcare.\n\nMy background aligns particularly well with this role through %s. I am based in Budapest, speak Arabic natively and English at C1 level, and I am available for student-compatible on-site or hybrid work within my approved weekly-hour limit.\n\nI would welcome the opportunity to discuss how my operational experience and MBA studies can support your team.\n\nKind regards,\n%s\n%s\n%s\n%s", job.Title, job.Company, strings.ToLower(job.Track), p.Name, p.Phone, p.Email, p.LinkedIn)
}

func writePreparedApplication(job Job, p CandidateProfile, cfg Config) error {
	if err := ensureDirs(); err != nil {
		return err
	}
	content := fmt.Sprintf("HAYA JOB AUTOPILOT v3\n\nJob: %s\nCompany: %s\nURL: %s\nMatch: %d%%\nRisk: %d/100\nStatus: %s\n\nWHY IT MATCHES\n- %s\n\nCONCERNS\n- %s\n\nAPPLICATION MESSAGE\n%s\n\nVERIFIED PROFILE ANSWERS\nWork authorization: %s\nSponsorship required: %s\nEarliest start date: %s\nMaximum weekly hours: %d\nMinimum gross monthly HUF: %s\n", job.Title, job.Company, job.URL, job.Score, job.Risk, job.Status, strings.Join(job.Reasons, "\n- "), strings.Join(job.Concerns, "\n- "), coverLetter(job, p, cfg), cfg.WorkAuthorizationHungary, cfg.SponsorshipRequired, cfg.EarliestStartDate, cfg.MaximumWeeklyHours, cfg.MinimumGrossMonthlyHUF)
	return os.WriteFile(filepath.Join(applicationsDir(), job.ID+".txt"), []byte(content), 0600)
}

func budapestDay(t time.Time) string {
	loc, err := time.LoadLocation("Europe/Budapest")
	if err != nil {
		return t.Format("2006-01-02")
	}
	return t.In(loc).Format("2006-01-02")
}
func mapKeysSortedLimited(m map[string]bool, limit int) []string {
	out := make([]string, 0, len(m))
	for k := range m {
		out = append(out, k)
	}
	sort.Strings(out)
	if len(out) > limit {
		out = out[len(out)-limit:]
	}
	return out
}
func parseInt(s string, def int) int {
	n, e := strconv.Atoi(strings.TrimSpace(s))
	if e != nil {
		return def
	}
	return n
}
