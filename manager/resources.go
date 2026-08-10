package main

import _ "embed"

//go:embed resources/Haya_Saadeh_CV.pdf
var embeddedCV []byte

//go:embed resources/haya_profile.json
var embeddedProfileJSON []byte

//go:embed resources/index.html
var dashboardHTML []byte

//go:embed resources/app.css
var dashboardCSS []byte

//go:embed resources/app.js
var dashboardJS []byte
