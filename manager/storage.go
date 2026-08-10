package main

import (
	"encoding/json"
	"errors"
	"fmt"
	"os"
	"path/filepath"
	"sync"
	"time"
)

var storageMu sync.Mutex

func defaultConfig() Config {
	return Config{
		Version:               3,
		Email:                 "saadehhaya@yahoo.com",
		DailyApplicationLimit: 5,
		AutomationMode:        "Prepare & Ask",
		MinimumMatchScore:     80,
		MaximumFraudRisk:      20,
		MaximumWeeklyHours:    30,
		ScanIntervalHours:     6,
	}
}

func loadProfile() (CandidateProfile, error) {
	var p CandidateProfile
	if err := json.Unmarshal(embeddedProfileJSON, &p); err != nil {
		return p, err
	}
	return p, nil
}

func loadConfig() (Config, error) {
	storageMu.Lock()
	defer storageMu.Unlock()
	cfg := defaultConfig()
	b, err := os.ReadFile(configPath())
	if errors.Is(err, os.ErrNotExist) {
		return cfg, nil
	}
	if err != nil {
		return cfg, err
	}
	if err := json.Unmarshal(b, &cfg); err != nil {
		return defaultConfig(), err
	}
	if cfg.Email == "" {
		cfg.Email = "saadehhaya@yahoo.com"
	}
	if cfg.DailyApplicationLimit <= 0 {
		cfg.DailyApplicationLimit = 5
	}
	if cfg.AutomationMode == "" {
		cfg.AutomationMode = "Prepare & Ask"
	}
	if cfg.MinimumMatchScore <= 0 {
		cfg.MinimumMatchScore = 80
	}
	if cfg.MaximumFraudRisk <= 0 {
		cfg.MaximumFraudRisk = 20
	}
	if cfg.MaximumWeeklyHours <= 0 {
		cfg.MaximumWeeklyHours = 30
	}
	if cfg.ScanIntervalHours <= 0 {
		cfg.ScanIntervalHours = 6
	}
	return cfg, nil
}

func saveConfig(cfg Config) error {
	storageMu.Lock()
	defer storageMu.Unlock()
	if err := ensureDirs(); err != nil {
		return err
	}
	cfg.Version = 3
	return writeJSONAtomicUnlocked(configPath(), cfg, 0600)
}

func loadState() (State, error) {
	storageMu.Lock()
	defer storageMu.Unlock()
	s := State{ApplicationsByDay: map[string]int{}}
	b, err := os.ReadFile(statePath())
	if errors.Is(err, os.ErrNotExist) {
		return s, nil
	}
	if err != nil {
		return s, err
	}
	if err := json.Unmarshal(b, &s); err != nil {
		return State{ApplicationsByDay: map[string]int{}}, err
	}
	if s.ApplicationsByDay == nil {
		s.ApplicationsByDay = map[string]int{}
	}
	return s, nil
}

func saveState(s State) error {
	storageMu.Lock()
	defer storageMu.Unlock()
	if s.ApplicationsByDay == nil {
		s.ApplicationsByDay = map[string]int{}
	}
	return writeJSONAtomicUnlocked(statePath(), s, 0600)
}

func loadJobs() ([]Job, error) {
	storageMu.Lock()
	defer storageMu.Unlock()
	var jobs []Job
	b, err := os.ReadFile(jobsPath())
	if errors.Is(err, os.ErrNotExist) {
		return []Job{}, nil
	}
	if err != nil {
		return nil, err
	}
	if err := json.Unmarshal(b, &jobs); err != nil {
		return nil, err
	}
	return jobs, nil
}

func saveJobs(jobs []Job) error {
	storageMu.Lock()
	defer storageMu.Unlock()
	if len(jobs) > 2500 {
		jobs = jobs[:2500]
	}
	return writeJSONAtomicUnlocked(jobsPath(), jobs, 0600)
}

func writeJSONAtomicUnlocked(path string, v any, mode os.FileMode) error {
	if err := os.MkdirAll(filepath.Dir(path), 0700); err != nil {
		return err
	}
	data, err := json.MarshalIndent(v, "", "  ")
	if err != nil {
		return err
	}
	data = append(data, '\n')
	tmp := path + ".tmp"
	if err := os.WriteFile(tmp, data, mode); err != nil {
		return err
	}
	if _, err := os.Stat(path); err == nil {
		_ = copyFile(path, path+".bak")
	}
	if err := os.Rename(tmp, path); err != nil {
		_ = os.Remove(path)
		if err2 := os.Rename(tmp, path); err2 != nil {
			return err
		}
	}
	return nil
}

func copyFile(src, dst string) error {
	in, err := os.Open(src)
	if err != nil {
		return err
	}
	defer in.Close()
	if err := os.MkdirAll(filepath.Dir(dst), 0700); err != nil {
		return err
	}
	out, err := os.Create(dst)
	if err != nil {
		return err
	}
	_, cpErr := out.ReadFrom(in)
	closeErr := out.Close()
	if cpErr != nil {
		return cpErr
	}
	return closeErr
}

func appendLog(message string, fields map[string]any) {
	_ = ensureDirs()
	record := map[string]any{"time": time.Now().Format(time.RFC3339), "message": message}
	for k, v := range fields {
		record[k] = v
	}
	b, _ := json.Marshal(record)
	f, err := os.OpenFile(filepath.Join(logDir(), "agent.log"), os.O_CREATE|os.O_WRONLY|os.O_APPEND, 0600)
	if err != nil {
		return
	}
	defer f.Close()
	_, _ = fmt.Fprintln(f, string(b))
}
