package main

import "time"

type CandidateProfile struct {
	Name          string            `json:"name"`
	Location      string            `json:"location"`
	Phone         string            `json:"phone"`
	Email         string            `json:"email"`
	LinkedIn      string            `json:"linkedin"`
	Headline      string            `json:"headline"`
	Availability  Availability      `json:"availability"`
	Languages     map[string]string `json:"languages"`
	PriorityRoles []string          `json:"priority_roles"`
	Skills        []string          `json:"skills"`
}

type Availability struct {
	MinimumHoursWeekly            int      `json:"minimum_hours_weekly"`
	MaximumHoursWeeklyDuringStudy int      `json:"maximum_hours_weekly_during_study"`
	WorkModes                     []string `json:"work_modes"`
}

type Config struct {
	Version                  int      `json:"version"`
	Email                    string   `json:"email"`
	EncryptedMailAppPassword string   `json:"encrypted_mail_app_password"`
	WorkAuthorizationHungary string   `json:"work_authorization_hungary"`
	SponsorshipRequired      string   `json:"sponsorship_required"`
	EarliestStartDate        string   `json:"earliest_start_date"`
	MinimumGrossMonthlyHUF   string   `json:"minimum_gross_monthly_salary_huf"`
	DailyApplicationLimit    int      `json:"daily_application_limit"`
	AutomationMode           string   `json:"automation_mode"`
	MinimumMatchScore        int      `json:"minimum_match_score"`
	MaximumFraudRisk         int      `json:"maximum_fraud_risk"`
	MaximumWeeklyHours       int      `json:"maximum_weekly_hours"`
	ScanIntervalHours        int      `json:"scan_interval_hours"`
	AllowedEmployerDomains   []string `json:"allowed_employer_domains"`
	ExcludedCompanies        []string `json:"excluded_companies"`
	JobAlertSenderContains   []string `json:"job_alert_sender_contains"`
}

type State struct {
	SeenMessageIDs    []string       `json:"seen_message_ids"`
	LastRun           *time.Time     `json:"last_run"`
	LastError         string         `json:"last_error"`
	LastRunReason     string         `json:"last_run_reason"`
	ApplicationsByDay map[string]int `json:"applications_by_day"`
	AgentPID          int            `json:"agent_pid"`
}

type Job struct {
	ID               string     `json:"id"`
	Title            string     `json:"title"`
	Company          string     `json:"company"`
	URL              string     `json:"url"`
	Source           string     `json:"source"`
	FoundAt          time.Time  `json:"foundAt"`
	Score            int        `json:"score"`
	Risk             int        `json:"risk"`
	Track            string     `json:"track"`
	Decision         string     `json:"decision"`
	Status           string     `json:"status"`
	ApplicationEmail string     `json:"applicationEmail,omitempty"`
	Reasons          []string   `json:"reasons"`
	Concerns         []string   `json:"concerns"`
	RiskSignals      []string   `json:"riskSignals"`
	AutoSubmitBlocks []string   `json:"autoSubmitBlocks"`
	AppliedAt        *time.Time `json:"appliedAt,omitempty"`
	SMTPMessageID    string     `json:"smtpMessageId,omitempty"`
	SourceMessageID  string     `json:"sourceMessageId,omitempty"`
	RawText          string     `json:"-"`
}

type MailLead struct {
	MessageID string
	Subject   string
	From      string
	Text      string
	HTML      string
	Links     []string
}

type PageData struct {
	URL   string
	Title string
	Text  string
	HTML  string
}
