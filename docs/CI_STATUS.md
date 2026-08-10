# CI status

## Current repository state

The source, build workflows, Windows smoke workflow, Docker agent, documentation, and Haya CV are committed to `main`.

## Hosted-runner blocker

GitHub Actions detected both workflows after the v2.0.1/v2.0.2 source publication, but GitHub did not start the hosted jobs. GitHub's check annotation reports:

> The job was not started because recent account payments have failed or your spending limit needs to be increased. Please check the 'Billing & plans' section in your settings.

This is an account-level GitHub Actions runner/billing condition, not a source compilation failure.

## Local release validation

The v2.0.2 release was validated outside GitHub-hosted Actions with:

- embedded payload generation: PASS
- `node --check agent/server.js`: PASS
- `config/haya_profile.json`: valid JSON
- `config/private_answers.template.json`: valid JSON
- anti-popup regression assertions: PASS
- Go cross-build for `windows/amd64`: PASS
- output format: PE32+ Windows GUI x86-64
- installer SHA-256: `900f4602e05e02610ede584ba1703dce94295a8c530486fd4504308c869d1207`

## To restore GitHub CI

Resolve the GitHub account payment/spending-limit notice under Billing & plans, then re-run the failed Actions workflows. No source change is required solely for this runner-start failure.
