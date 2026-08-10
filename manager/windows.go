package main

import (
	"encoding/base64"
	"errors"
	"fmt"
	"os"
	"os/exec"
	"path/filepath"
	"strings"
	"syscall"
	"time"
	"unsafe"
)

const (
	createNoWindow     = 0x08000000
	errorAlreadyExists = 183
)

var (
	user32   = syscall.NewLazyDLL("user32.dll")
	kernel32 = syscall.NewLazyDLL("kernel32.dll")
	shell32  = syscall.NewLazyDLL("shell32.dll")
	crypt32  = syscall.NewLazyDLL("crypt32.dll")

	pShellExecuteW      = shell32.NewProc("ShellExecuteW")
	pCreateMutexW       = kernel32.NewProc("CreateMutexW")
	pCloseHandleW       = kernel32.NewProc("CloseHandle")
	pLocalFree          = kernel32.NewProc("LocalFree")
	pCryptProtectData   = crypt32.NewProc("CryptProtectData")
	pCryptUnprotectData = crypt32.NewProc("CryptUnprotectData")
)

type dataBlob struct {
	cbData uint32
	pbData *byte
}

func utf16Ptr(s string) *uint16 {
	p, _ := syscall.UTF16PtrFromString(s)
	return p
}

func hiddenProcAttr() *syscall.SysProcAttr {
	return &syscall.SysProcAttr{HideWindow: true, CreationFlags: createNoWindow}
}

func appDir() string {
	base := os.Getenv("LOCALAPPDATA")
	if base == "" {
		base = filepath.Join(os.Getenv("USERPROFILE"), "AppData", "Local")
	}
	return filepath.Join(base, "HayaJobAutopilot")
}

func dataDir() string          { return filepath.Join(appDir(), "data") }
func logDir() string           { return filepath.Join(appDir(), "logs") }
func applicationsDir() string  { return filepath.Join(appDir(), "applications") }
func docsDir() string          { return filepath.Join(appDir(), "documents") }
func configPath() string       { return filepath.Join(dataDir(), "config.json") }
func statePath() string        { return filepath.Join(dataDir(), "state.json") }
func jobsPath() string         { return filepath.Join(dataDir(), "jobs.json") }
func cvPath() string           { return filepath.Join(docsDir(), "Haya_Saadeh_CV.pdf") }
func installedExePath() string { return filepath.Join(appDir(), "Haya Job Autopilot.exe") }

func ensureDirs() error {
	for _, d := range []string{appDir(), dataDir(), logDir(), applicationsDir(), docsDir()} {
		if err := os.MkdirAll(d, 0700); err != nil {
			return err
		}
	}
	return nil
}

func createNamedMutex(name string) (syscall.Handle, bool, error) {
	h, _, callErr := pCreateMutexW.Call(0, 1, uintptr(unsafe.Pointer(utf16Ptr(name))))
	if h == 0 {
		return 0, false, callErr
	}
	already := false
	if errno, ok := callErr.(syscall.Errno); ok && errno == errorAlreadyExists {
		already = true
	}
	return syscall.Handle(h), already, nil
}

func closeHandle(h syscall.Handle) {
	if h != 0 {
		pCloseHandleW.Call(uintptr(h))
	}
}

func openTarget(target string) {
	pShellExecuteW.Call(0, uintptr(unsafe.Pointer(utf16Ptr("open"))), uintptr(unsafe.Pointer(utf16Ptr(target))), 0, 0, 1)
}

func protectString(s string) (string, error) {
	if s == "" {
		return "", nil
	}
	src := []byte(s)
	in := dataBlob{cbData: uint32(len(src)), pbData: &src[0]}
	var out dataBlob
	r, _, e := pCryptProtectData.Call(
		uintptr(unsafe.Pointer(&in)),
		0, 0, 0, 0, 0,
		uintptr(unsafe.Pointer(&out)),
	)
	if r == 0 {
		return "", fmt.Errorf("CryptProtectData: %w", e)
	}
	defer pLocalFree.Call(uintptr(unsafe.Pointer(out.pbData)))
	b := unsafe.Slice(out.pbData, out.cbData)
	return base64.StdEncoding.EncodeToString(append([]byte(nil), b...)), nil
}

func unprotectString(encoded string) (string, error) {
	if encoded == "" {
		return "", nil
	}
	raw, err := base64.StdEncoding.DecodeString(encoded)
	if err != nil {
		return "", err
	}
	if len(raw) == 0 {
		return "", nil
	}
	in := dataBlob{cbData: uint32(len(raw)), pbData: &raw[0]}
	var out dataBlob
	r, _, e := pCryptUnprotectData.Call(
		uintptr(unsafe.Pointer(&in)),
		0, 0, 0, 0, 0,
		uintptr(unsafe.Pointer(&out)),
	)
	if r == 0 {
		return "", fmt.Errorf("CryptUnprotectData: %w", e)
	}
	defer pLocalFree.Call(uintptr(unsafe.Pointer(out.pbData)))
	b := unsafe.Slice(out.pbData, out.cbData)
	return string(append([]byte(nil), b...)), nil
}

func registerAutostart() error {
	value := fmt.Sprintf(`"%s" --agent`, installedExePath())
	cmd := exec.Command("reg.exe", "add", `HKCU\Software\Microsoft\Windows\CurrentVersion\Run`, "/v", "HayaJobAutopilot", "/t", "REG_SZ", "/d", value, "/f")
	cmd.SysProcAttr = hiddenProcAttr()
	out, err := cmd.CombinedOutput()
	if err != nil {
		return fmt.Errorf("registering startup: %v: %s", err, strings.TrimSpace(string(out)))
	}
	return nil
}

func removeAutostart() {
	cmd := exec.Command("reg.exe", "delete", `HKCU\Software\Microsoft\Windows\CurrentVersion\Run`, "/v", "HayaJobAutopilot", "/f")
	cmd.SysProcAttr = hiddenProcAttr()
	_ = cmd.Run()
}

func startAgentProcess() error {
	exe := installedExePath()
	if _, err := os.Stat(exe); err != nil {
		return errors.New("Haya Job Autopilot is not installed yet")
	}
	cmd := exec.Command(exe, "--agent")
	cmd.SysProcAttr = hiddenProcAttr()
	return cmd.Start()
}

func stopAgentProcess() error {
	req, err := httpPostLocal("http://127.0.0.1:8787/api/shutdown", []byte(`{}`), 5)
	if err != nil {
		return err
	}
	if req < 200 || req >= 300 {
		return fmt.Errorf("agent returned HTTP %d", req)
	}
	return nil
}

func copySelfToInstall() error {
	current, err := os.Executable()
	if err != nil {
		return err
	}
	current, _ = filepath.Abs(current)
	dst := installedExePath()
	if strings.EqualFold(filepath.Clean(current), filepath.Clean(dst)) {
		return nil
	}
	srcInfo, err := os.Stat(current)
	if err != nil {
		return err
	}
	if dstInfo, err := os.Stat(dst); err == nil && dstInfo.Size() == srcInfo.Size() {
		// Still copy on upgrade when possible; equal size is not proof of identity.
	}
	data, err := os.ReadFile(current)
	if err != nil {
		return err
	}
	tmp := dst + ".new"
	if err := os.WriteFile(tmp, data, 0700); err != nil {
		return err
	}
	var lastErr error
	for attempt := 0; attempt < 15; attempt++ {
		_ = os.Remove(dst)
		if err := os.Rename(tmp, dst); err == nil {
			return nil
		} else {
			lastErr = err
		}
		time.Sleep(250 * time.Millisecond)
	}
	_ = os.Remove(tmp)
	return fmt.Errorf("replacing installed executable: %w", lastErr)
}

func removeInstalledFilesAfterExit() error {
	current, _ := os.Executable()
	dir := appDir()
	if !strings.HasPrefix(strings.ToLower(filepath.Clean(current)), strings.ToLower(filepath.Clean(dir))) {
		return os.RemoveAll(dir)
	}
	bat := filepath.Join(os.TempDir(), "remove_haya_job_autopilot.cmd")
	script := "@echo off\r\ntimeout /t 3 /nobreak >nul\r\nrmdir /s /q \"" + dir + "\"\r\ndel /q \"%~f0\"\r\n"
	if err := os.WriteFile(bat, []byte(script), 0600); err != nil {
		return err
	}
	cmd := exec.Command("cmd.exe", "/c", "start", "", "/min", bat)
	cmd.SysProcAttr = hiddenProcAttr()
	return cmd.Start()
}
