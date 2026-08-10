# Authoritative v3 source

`Haya_Job_Autopilot_v3.0.0_Source.zip` is the exact source archive used to produce the locally validated v3.0.0 No Docker Edition.

SHA-256:

`f6e400144dafad0b7f19a3b30edfcb6fdfbc23d0ed17c4c8142b6de1fc5027f1`

The archive includes the Go manager/background-agent source, embedded dashboard assets, Haya profile/CV, configuration templates, documentation, and workflow definitions.

GitHub CI first verifies this SHA-256, extracts the archive into a clean build directory, then runs JSON/CV validation, regression guards, Go vet, and the Windows x64 GUI build.
