package main

import (
	"archive/zip"
	"bytes"
	"context"
	_ "embed"
	"errors"
	"fmt"
	"io"
	"net/http"
	"os"
	"os/exec"
	"path/filepath"
	"runtime"
	"strconv"
	"strings"
	"sync/atomic"
	"syscall"
	"time"
	"unsafe"
)

//go:embed payload.zip
var payload []byte

const (
	appTitle                = "Haya Job Autopilot Manager"
	appVersion              = "2.0.1"
	WS_OVERLAPPED           = 0x00000000
	WS_CAPTION              = 0x00C00000
	WS_SYSMENU              = 0x00080000
	WS_THICKFRAME           = 0x00040000
	WS_MINIMIZEBOX          = 0x00020000
	WS_MAXIMIZEBOX          = 0x00010000
	WS_VISIBLE              = 0x10000000
	WS_CHILD                = 0x40000000
	WS_TABSTOP              = 0x00010000
	WS_BORDER               = 0x00800000
	BS_PUSHBUTTON           = 0
	BS_DEFPUSHBUTTON        = 1
	BS_AUTOCHECKBOX         = 3
	SS_LEFT                 = 0
	SS_NOPREFIX             = 0x80
	ES_PASSWORD             = 0x20
	ES_AUTOHSCROLL          = 0x80
	ES_NUMBER               = 0x2000
	CBS_DROPDOWNLIST        = 3
	WM_CREATE               = 1
	WM_DESTROY              = 2
	WM_SIZE                 = 5
	WM_COMMAND              = 0x111
	WM_GETMINMAXINFO        = 0x24
	WM_CLOSE                = 0x10
	WM_SETFONT              = 0x30
	WM_APP                  = 0x8000
	WM_UPDATE               = WM_APP + 10
	BN_CLICKED              = 0
	CBN_SELCHANGE           = 1
	SW_SHOW                 = 5
	SW_HIDE                 = 0
	SW_SHOWNORMAL           = 1
	MB_OK                   = 0
	MB_ICONINFORMATION      = 0x40
	MB_ICONWARNING          = 0x30
	MB_ICONERROR            = 0x10
	MB_YESNO                = 4
	MB_DEFBUTTON2           = 0x100
	IDYES                   = 6
	SEE_MASK_NOCLOSEPROCESS = 0x40
	CREATE_NO_WINDOW        = 0x08000000
	ERROR_ALREADY_EXISTS    = 183
	IDC_ARROW               = 32512
	COLOR_WINDOW            = 5
	ID_INSTALL              = 101
	ID_CHECK                = 102
	ID_SAVE                 = 103
	ID_REPLACE_CV           = 104
	ID_START                = 105
	ID_STOP                 = 106
	ID_RESTART              = 107
	ID_OPEN                 = 108
	ID_RUN                  = 109
	ID_LOGS                 = 110
	ID_FOLDER               = 111
	ID_UNINSTALL            = 112
	ID_PASS                 = 201
	ID_AUTH                 = 202
	ID_SPONSOR              = 203
	ID_STARTDATE            = 204
	ID_SALARY               = 205
	ID_LIMIT                = 206
	ID_AUTO                 = 207
	ID_REMOVE_DOCKER        = 208
	ID_REMOVE_WSL           = 209
	ID_BACKUP               = 210
)

var user32 = syscall.NewLazyDLL("user32.dll")
var kernel32 = syscall.NewLazyDLL("kernel32.dll")
var gdi32 = syscall.NewLazyDLL("gdi32.dll")
var shell32 = syscall.NewLazyDLL("shell32.dll")
var pRegisterClass = user32.NewProc("RegisterClassExW")
var pCreateWindow = user32.NewProc("CreateWindowExW")
var pDefWindow = user32.NewProc("DefWindowProcW")
var pShowWindow = user32.NewProc("ShowWindow")
var pUpdateWindow = user32.NewProc("UpdateWindow")
var pGetMessage = user32.NewProc("GetMessageW")
var pTranslate = user32.NewProc("TranslateMessage")
var pDispatch = user32.NewProc("DispatchMessageW")
var pPostQuit = user32.NewProc("PostQuitMessage")
var pMoveWindow = user32.NewProc("MoveWindow")
var pGetClientRect = user32.NewProc("GetClientRect")
var pSetText = user32.NewProc("SetWindowTextW")
var pGetTextLen = user32.NewProc("GetWindowTextLengthW")
var pGetText = user32.NewProc("GetWindowTextW")
var pEnable = user32.NewProc("EnableWindow")
var pSend = user32.NewProc("SendMessageW")
var pPost = user32.NewProc("PostMessageW")
var pMessageBox = user32.NewProc("MessageBoxW")
var pLoadCursor = user32.NewProc("LoadCursorW")
var pSetDPI = user32.NewProc("SetProcessDPIAware")
var pCheck = user32.NewProc("CheckDlgButton")
var pIsChecked = user32.NewProc("IsDlgButtonChecked")
var pCreateFont = gdi32.NewProc("CreateFontW")
var pDeleteObject = gdi32.NewProc("DeleteObject")
var pGetModule = kernel32.NewProc("GetModuleHandleW")
var pWait = kernel32.NewProc("WaitForSingleObject")
var pExitCode = kernel32.NewProc("GetExitCodeProcess")
var pCloseHandle = kernel32.NewProc("CloseHandle")
var pCreateMutex = kernel32.NewProc("CreateMutexW")
var pShellExecute = shell32.NewProc("ShellExecuteW")
var pShellExecuteEx = shell32.NewProc("ShellExecuteExW")

type WNDCLASSEX struct {
	CbSize, Style                            uint32
	LpfnWndProc                              uintptr
	CbClsExtra, CbWndExtra                   int32
	HInstance, HIcon, HCursor, HbrBackground syscall.Handle
	LpszMenuName, LpszClassName              *uint16
	HIconSm                                  syscall.Handle
}
type POINT struct{ X, Y int32 }
type RECT struct{ Left, Top, Right, Bottom int32 }
type MSG struct {
	Hwnd           syscall.Handle
	Message        uint32
	WParam, LParam uintptr
	Time           uint32
	Pt             POINT
	LPrivate       uint32
}
type MINMAXINFO struct{ PtReserved, PtMaxSize, PtMaxPosition, PtMinTrackSize, PtMaxTrackSize POINT }
type SHELLEXECUTEINFO struct {
	CbSize, FMask                             uint32
	Hwnd                                      syscall.Handle
	LpVerb, LpFile, LpParameters, LpDirectory *uint16
	NShow                                     int32
	HInstApp                                  syscall.Handle
	LpIDList                                  uintptr
	LpClass                                   *uint16
	HkeyClass                                 syscall.Handle
	DwHotKey                                  uint32
	HIconOrMonitor, HProcess                  syscall.Handle
}

type update struct {
	status, detail, msg string
	busy                *bool
	kind                uint32
}
type state struct {
	hwnd, hinst, font, bold, title syscall.Handle
	controls                       map[int]syscall.Handle
	named                          map[string]syscall.Handle
	updates                        chan update
	busy                           bool
	installing                     atomic.Bool
	instanceMutex                  syscall.Handle
}

var app state

func u16(s string) *uint16 { p, _ := syscall.UTF16PtrFromString(s); return p }
func lo(v uintptr) uint16  { return uint16(v & 0xffff) }
func hi(v uintptr) uint16  { return uint16(v >> 16) }
func main() {
	runtime.LockOSThread()
	pSetDPI.Call()
	mutex, _, mutexErr := pCreateMutex.Call(0, 1, uintptr(unsafe.Pointer(u16("Local\\HayaJobAutopilotManagerSingleInstance"))))
	if mutex == 0 {
		panic(mutexErr)
	}
	app.instanceMutex = syscall.Handle(mutex)
	if errno, ok := mutexErr.(syscall.Errno); ok && errno == ERROR_ALREADY_EXISTS {
		box("Haya Job Autopilot Manager is already open. Use the existing window instead of starting another installer.", MB_OK|MB_ICONINFORMATION)
		pCloseHandle.Call(mutex)
		return
	}
	defer pCloseHandle.Call(mutex)
	app.controls = map[int]syscall.Handle{}
	app.named = map[string]syscall.Handle{}
	app.updates = make(chan update, 64)
	h, _, _ := pGetModule.Call(0)
	app.hinst = syscall.Handle(h)
	c := u16("HayaManagerV2")
	cur, _, _ := pLoadCursor.Call(0, IDC_ARROW)
	wc := WNDCLASSEX{CbSize: uint32(unsafe.Sizeof(WNDCLASSEX{})), LpfnWndProc: syscall.NewCallback(wndProc), HInstance: app.hinst, HCursor: syscall.Handle(cur), HbrBackground: syscall.Handle(COLOR_WINDOW + 1), LpszClassName: c}
	if a, _, e := pRegisterClass.Call(uintptr(unsafe.Pointer(&wc))); a == 0 {
		panic(e)
	}
	style := uintptr(WS_OVERLAPPED | WS_CAPTION | WS_SYSMENU | WS_THICKFRAME | WS_MINIMIZEBOX | WS_MAXIMIZEBOX | WS_VISIBLE)
	hw, _, e := pCreateWindow.Call(0, uintptr(unsafe.Pointer(c)), uintptr(u²È="24(%É•ÑÕÉ¸¹¥°)ô)™Õ¹ŒÍ…Ù•AÉ¥Ù…Ñ” ¤•ÉÉ½Èì(%Á…ÍÌ€èô•ÑQ•áĞ¡…ÁÀ¹½¹ÑÉ½±Ím%}AMMt¤(%…ÕÑ €èô•ÑQ•áĞ¡…ÁÀ¹½¹ÑÉ½±Ím%}UQ!t¤(%Í¤°|°|€èôÁM•¹¹…±°¡Õ¥¹ÑÁÑÈ¡…ÁÀ¹½¹ÑÉ½±Ím%}MA=9M=It¤°€ÁàÀÄĞÜ°€À°€À¤(%ÍÁ½¹Í½È€èô€ˆˆ(%¥˜Í¤€ôô€Äì($%ÍÁ½¹Í½È€ô€‰9¼ˆ(%ô•±Í”¥˜Í¤€ôô€Èì($%ÍÁ½¹Í½È€ô€‰e•Ìˆ(%ô(%‘…Ñ”€èô•ÑQ•áĞ¡…ÁÀ¹½¹ÑÉ½±Ím%}MQIQQt¤(%Í…±…Éä€èô•ÑQ•áĞ¡…ÁÀ¹½¹ÑÉ½±Ím%}M1Iet¤(%±¥´°”€èôÍÑÉ½¹Ø¹Ñ½¤¡ÍÑÉ¥¹Ì¹QÉ¥µMÁ…”¡•ÑQ•áĞ¡…ÁÀ¹½¹ÑÉ½±Ím%}1%5%Qt¤¤¤(%¥˜”€„ô¹¥°ñğ±¥´€ğ€Äñğ±¥´€ø€ÈÀì($%É•ÑÕÉ¸•ÉÉ½ÉÌ¹9•Ü ‰ÕÑ½µ…Ñ¥Œ…ÁÁ±¥…Ñ¥½¹Ì½‘…äµÕÍĞ‰”€Ä´ÈÀˆ¤(%ô(%…ÕÑ¼€èô¡•­•¡%}UQ<¤(%¥˜…ÕÑ¼€˜˜€¡Á…ÍÌ€ôô€ˆˆñğ…ÕÑ €ôô€ˆˆñğÍÁ½¹Í½È€ôô€ˆˆñğ‘…Ñ”€ôô€ˆˆ¤ì($%É•ÑÕÉ¸•ÉÉ½ÉÌ¹9•Ü ‰ÕÑ¼µ…ÁÁ±ä¹••‘Ìe…¡½¼…ÁÀÁ…ÍÍİ½É°İ½É¬…ÕÑ¡½É¥é…Ñ¥½¸°ÍÁ½¹Í½ÉÍ¡¥À…¹Íİ•È…¹ÍÑ…ÉĞ‘…Ñ”ˆ¤(%ô(%¥˜”€ô•áÑÉ…Ğ ¤ì”€„ô¹¥°ì($%É•ÑÕÉ¸”(%ô(%•¹Ø€èômuÍÑÉ¥¹ì‰!e}5%0õp‰Í……‘•¡¡…å…å…¡½¼¹½µpˆˆ°€‰%5A}!=MPõp‰¥µ…À¹µ…¥°¹å…¡½¼¹½µpˆˆ°€‰%5A}A=IPôääÌˆ°€‰M5QA}!=MPõp‰ÍµÑÀ¹µ…¥°¹å…¡½¼¹½µpˆˆ°€‰M5QA}A=IPôĞØÔˆ°€‰5%1}AA}AMM]=Iôˆ€¬•¹ÙÄ¡Á…ÍÌ¤°€‰M9}M!U1õpˆÀ€¨¼Ø€¨€¨€©pˆˆ°€‰5%9}5Q!}M=IôàÀˆ°€‰5a}IU}I%M,ôÌÀˆ°€‰5a}]-1e}!=UILôÌÀˆ°€‰%1e}AA1%Q%=9}1%5%Pôˆ€¬ÍÑÉ½¹Ø¹%Ñ½„¡±¥´¤°€‰UQ=}5%1}AA1dôˆ€¬ÍÑÉ½¹Ø¹½Éµ…Ñ	½½°¡…ÕÑ¼¤°€‰]=I-}UQ!=I%iQ%=9}!U9Idôˆ€¬•¹ÙÄ¡…ÕÑ ¤°€‰MA=9M=IM!%A}IEU%Iôˆ€¬•¹ÙÄ¡ÍÁ½¹Í½È¤°€‰I1%MQ}MQIQ}Qôˆ€¬•¹ÙÄ¡‘…Ñ”¤°€‰5%9}I=MM}5=9Q!1e}M1Ie}!Uôˆ€¬•¹ÙÄ¡Í…±…Éä¤°€‰Y}AQ õpˆ½…ÁÀ½‘½Õµ•¹ÑÌ½!…å…}M……‘•¡}X¹Á‘™pˆ‰ô(%¥˜”€ô½Ì¹]É¥Ñ•¥±”¡™¥±•Á…Ñ ¹)½¥¸¡¥¹ÍÑ…±±¥È ¤°€ˆ¹•¹Øˆ¤°mu‰åÑ”¡ÍÑÉ¥¹Ì¹)½¥¸¡•¹Ø°€‰qÉq¸ˆ¤¬‰qÉq¸ˆ¤°€ÀØÀÀ¤ì”€„ô¹¥°ì($%É•ÑÕÉ¸”(%ô(%¥˜|°”€ô™¥¹‘½­•È ¤ì”€ôô¹¥°ì($%É•ÑÕÉ¸½µÁ½Í” ‰ÕÀˆ°€ˆµˆ°€ˆ´µ™½É”µÉ•É•…Ñ”ˆ¤(%ô(%É•ÑÕÉ¸¹¥°)ô)™Õ¹Œ•¹ÙÄ¡ÌÍÑÉ¥¹œ¤ÍÑÉ¥¹œì(%Ì€ôÍÑÉ¥¹Ì¹I•Á±…•±°¡Ì°€‰qpˆ°€‰qqqpˆ¤(%Ì€ôÍÑÉ¥¹Ì¹I•Á±…•±°¡Ì°€‰pˆˆ°€‰qqpˆˆ¤(%Ì€ôÍÑÉ¥¹Ì¹I•Á±…•±°¡Ì°€‰qÈˆ°€ˆ€ˆ¤(%Ì€ôÍÑÉ¥¹Ì¹I•Á±…•±°¡Ì°€‰q¸ˆ°€ˆ€ˆ¤(%É•ÑÕÉ¸€‰pˆˆ€¬ÍÑÉ¥¹Ì¹QÉ¥µMÁ…”¡Ì¤€¬€‰pˆˆ)ô)™Õ¹ŒÉ•Á±…•X ¤ì(%ÍÉ¥ÁĞ€èô‘µQåÁ”€µÍÍ•µ‰±å9…µ”MåÍÑ•´¹]¥¹‘½İÌ¹½ÉµÌì‘õ9•Üµ=‰©•ĞMåÍÑ•´¹]¥¹‘½İÌ¹½ÉµÌ¹=Á•¹¥±•¥…±½œì‘¹¥±Ñ•ÈôA™¥±•Ì€ ¨¹Á‘˜¥ğ¨¹Á‘˜œí¥˜ ‘¹M¡½İ¥…±½œ ¤€µ•Ä€=,œ¥ím½¹Í½±•tèé]É¥Ñ” ‘¹¥±•9…µ”¥õ€(%Ñà°Œ€èô½¹Ñ•áĞ¹]¥Ñ¡Q¥µ•½ÕĞ¡½¹Ñ•áĞ¹	…­É½Õ¹ ¤°€È©Ñ¥µ”¹5¥¹ÕÑ”¤(%‘•™•ÈŒ ¤(%µ€èô•á•Œ¹½µµ…¹‘½¹Ñ•áĞ¡Ñà°€‰Á½İ•ÉÍ¡•±°¹•á”ˆ°€ˆµ9½1½¼ˆ°€ˆµ9½AÉ½™¥±”ˆ°€ˆµMQˆ°€ˆµ½µµ…¹ˆ°ÍÉ¥ÁĞ¤(%µ¹MåÍAÉ½ÑÑÈ€ô¡¥‘‘•¸ ¤(%½ÕĞ°”€èôµ¹=ÕÑÁÕĞ ¤(%¥˜”€„ô¹¥°ì($%É•ÑÕÉ¸(%ô(%ÍÉŒ€èôÍÑÉ¥¹Ì¹QÉ¥µMÁ…”¡ÍÑÉ¥¹œ¡½ÕĞ¤¤(%¥˜ÍÉŒ€ôô€ˆˆì($%É•ÑÕÉ¸(%ô(%|€ô½Ì¹5­‘¥É±°¡™¥±•Á…Ñ ¹)½¥¸¡¥¹ÍÑ…±±¥È ¤°€‰‘½Õµ•¹ÑÌˆ¤°€ÀÜÔÔ¤(%¥˜”€ô½Áå¥±”¡ÍÉŒ°™¥±•Á…Ñ ¹)½¥¸¡¥¹ÍÑ…±±¥È ¤°€‰‘½Õµ•¹ÑÌˆ°€‰!…å…}M……‘•¡}X¹Á‘˜ˆ¤¤ì”€„ô¹¥°ì($%Á½ÍĞ¡ÕÁ‘…Ñ•íµÍœè”¹ÉÉ½È ¤°­¥¹è5	}%=9II=Iô¤(%ô•±Í”ì($%Á½ÍĞ¡ÕÁ‘…Ñ•íµÍœè€‰XÉ•Á±…•ÍÕ•ÍÍ™Õ±±ä¸ˆ°­¥¹è5	}%=9%9=I5Q%=9ô¤(%ô)ô)™Õ¹ŒÉÕ¹¡•¬ ¤•ÉÉ½Èì(%Œ€èô¡ÑÑÀ¹±¥•¹ÑíQ¥µ•½ÕĞè€È€¨Ñ¥µ”¹5¥¹ÕÑ•ô(%È°”€èôŒ¹A½ÍĞ ‰¡ÑÑÀè¼¼ÄÈÜ¸À¸À¸ÄèàÜàÜ½…Á¤½ÉÕ¸ˆ°€‰…ÁÁ±¥…Ñ¥½¸½©Í½¸ˆ°ÍÑÉ¥¹Ì¹9•İI•…‘•È ‰íôˆ¤¤(%¥˜”€„ô¹¥°ì($%É•ÑÕÉ¸”(%ô(%‘•™•ÈÈ¹	½‘ä¹±½Í” ¤(%ˆ°|€èô¥¼¹I•…‘±°¡¥¼¹1¥µ¥ÑI•…‘•È¡È¹	½‘ä°€ÄğğÈÀ¤¤(%¥˜È¹MÑ…ÑÕÍ½‘”€ğ€ÈÀÀñğÈ¹MÑ…ÑÕÍ½‘”€øô€ÌÀÀì($%É•ÑÕÉ¸™µĞ¹ÉÉ½É˜ ‰•¹Ğ!QQ@€•è€•Ìˆ°È¹MÑ…ÑÕÍ½‘”°Ñ…¥°¡ÍÑÉ¥¹œ¡ˆ¤°€ÜÀÀ¤¤(%ô(%É•ÑÕÉ¸¹¥°)ô)™Õ¹Œ…ÕÑ½ÍÑ…ÉĞ ¤•ÉÉ½Èì(%•á”°”€èô½Ì¹á•ÕÑ…‰±” ¤(%¥˜”€„ô¹¥°ì($%É•ÑÕÉ¸”(%ô(%µ€èô•á•Œ¹½µµ…¹ ‰É•œ¹•á”ˆ°€‰…‘ˆ°!-UqM½™Ñİ…É•q5¥É½Í½™Ñq]¥¹‘½İÍqÕÉÉ•¹ÑY•ÉÍ¥½¹qIÕ¹€°€½Ù€°!…å…)½‰ÕÑ½Á¥±½Ñ€°€½Ñ€°I}Mi€°€½‘€°Ä¡•á”¤°€½™€¤(%µ¹MåÍAÉ½ÑÑÈ€ô¡¥‘‘•¸ ¤(%É•ÑÕÉ¸µ¹IÕ¸ ¤)ô)™Õ¹ŒÕ¹¥¹ÍÑ…±±AÉ½µÁĞ ¤ì(%´€èô€‰I•µ½Ù”!…å„)½ˆÕÑ½Á¥±½Ğ±½…°™¥±•Ì…¹ÍÑ…ÉÑÕÀ•¹ÑÉäüˆ(%¥˜¡•­•¡%}I5=Y}=-H¤ì($%´€¬ô€‰q¹q¹½­•È•Í­Ñ½Àİ¥±°…±Í¼‰”Õ¹¥¹ÍÑ…±±•¸ˆ(%ô(%¥˜¡•­•¡%}I5=Y}]M0¤ì($%´€¬ô€‰q¹q¹]M0€È]¥¹‘½İÌ™•…ÑÕÉ•Ìİ¥±°…±Í¼‰”‘¥Í…‰±•¸ˆ(%ô(%¥˜‰½à¡´°5	}eM9=ñ5	}%=9]I9%9ñ5	}	UQQ=8È¤€„ô%eLì($%É•ÑÕÉ¸(%ô(%‰ÕÍä ‰U¹¥¹ÍÑ…±±¥¹œ¸¸¸ˆ°Õ¹¥¹ÍÑ…±°¤)ô)™Õ¹ŒÕ¹¥¹ÍÑ…±° ¤•ÉÉ½Èì(%€èô¥¹ÍÑ…±±¥È ¤(%¥˜¡•­•¡%}	-U@¤ì($%‘•Í¬€èô™¥±•Á…Ñ ¹)½¥¸¡½Ì¹•Ñ•¹Ø ‰UMIAI=%1ˆ¤°€‰•Í­Ñ½Àˆ°€‰!…å„)½ˆÕÑ½Á¥±½Ğ	…­ÕÀ€ˆ­Ñ¥µ”¹9½Ü ¤¹½Éµ…Ğ ˆÈÀÀØÀÄÀÉ|ÄÔÀĞÀÔˆ¤¤($%|€ô½Áå¥È¡™¥±•Á…Ñ ¹)½¥¸¡°€‰‘…Ñ„ˆ¤°™¥±•Á…Ñ ¹)½¥¸¡‘•Í¬°€‰‘…Ñ„ˆ¤¤($%|€ô½Áå¥È¡™¥±•Á…Ñ ¹)½¥¸¡°€‰…ÁÁ±¥…Ñ¥½¹Ìˆ¤°™¥±•Á…Ñ ¹)½¥¸¡‘•Í¬°€‰…ÁÁ±¥…Ñ¥½¹Ìˆ¤¤($%|€ô½Áå¥È¡™¥±•Á…Ñ ¹)½¥¸¡°€‰±½Ìˆ¤°™¥±•Á…Ñ ¹)½¥¸¡‘•Í¬°€‰±½Ìˆ¤¤(%ô(%|€ô½µÁ½Í•Q¥µ•½ÕĞ Ì©Ñ¥µ”¹5¥¹ÕÑ”°€‰‘½İ¸ˆ¤(%µ€èô•á•Œ¹½µµ…¹ ‰É•œ¹•á”ˆ°€‰‘•±•Ñ”ˆ°!-UqM½™Ñİ…É•q5¥É½Í½™Ñq]¥¹‘½İÍqÕÉÉ•¹ÑY•ÉÍ¥½¹qIÕ¹€°€½Ù€°!…å…)½‰ÕÑ½Á¥±½Ñ€°€½™€¤(%µ¹MåÍAÉ½ÑÑÈ€ô¡¥‘‘•¸ ¤(%|€ôµ¹IÕ¸ ¤(%¥˜¡•­•¡%}I5=Y}=-H¤ñğ¡•­•¡%}I5=Y}]M0¤ì($%ÁÌ€èômuÍÑÉ¥¹ìˆ‘ÉÉ½ÉÑ¥½¹AÉ•™•É•¹”ô½¹Ñ¥¹Õ”œ‰ô($%¥˜¡•­•¡%}I5=Y}=-H¤ì($$%ÁÌ€ô…ÁÁ•¹¡ÁÌ°€‘Œõ  ˆ‘•¹Øé1=1AAQqAÉ½É…µÍq½­•É•Í­Ñ½Áq½­•È•Í­Ñ½À%¹ÍÑ…±±•È¹•á”ˆ°ˆ‘•¹ØéAÉ½É…µ¥±•Íq½­•Éq½­•Éq½­•È•Í­Ñ½À%¹ÍÑ…±±•È¹•á”ˆ¤í™½É•…  ‘¤¥¸€‘Œ¥í¥˜¡Q•ÍĞµA…Ñ €‘¤¥íMÑ…ÉĞµAÉ½•ÍÌ€‘¤€µÉÕµ•¹Ñ1¥ÍĞ€Õ¹¥¹ÍÑ…±°œ°œ´µÅÕ¥•Ğœ€µ]…¥Ğí‰É•…­õõ€¤($%ô($%¥˜¡•­•¡%}I5=Y}]M0¤ì($$%ÁÌ€ô…ÁÁ•¹¡ÁÌ°¥Í…‰±”µ]¥¹‘½İÍ=ÁÑ¥½¹…±•…ÑÕÉ”€µ=¹±¥¹”€µ•…ÑÕÉ•9…µ”Y¥ÉÑÕ…±5…¡¥¹•A±…Ñ™½É´€µ9½I•ÍÑ…ÉÑ€°¥Í…‰±”µ]¥¹‘½İÍ=ÁÑ¥½¹…±•…ÑÕÉ”€µ=¹±¥¹”€µ•…ÑÕÉ•9…µ”5¥É½Í½™Ğµ]¥¹‘½İÌµMÕ‰ÍåÍÑ•´µ1¥¹Õà€µ9½I•ÍÑ…ÉÑ€¤($%ô($%˜€èô™¥±•Á…Ñ ¹)½¥¸¡½Ì¹Q•µÁ¥È ¤°€‰¡…å…}Õ¹¥¹ÍÑ…±°¹ÁÌÄˆ¤($%|€ô½Ì¹]É¥Ñ•¥±”¡˜°mu‰åÑ” ‰qáqá		qá	ˆ­ÍÑÉ¥¹Ì¹)½¥¸¡ÁÌ°€‰qÉq¸ˆ¤¤°€ÀØĞĞ¤($%¥˜|°”€èôÉÕ¹…Ì ‰Á½İ•ÉÍ¡•±°¹•á”ˆ°€ˆµ9½1½¼€µ9½AÉ½™¥±”€µá•ÕÑ¥½¹A½±¥ä	åÁ…ÍÌ€µ¥±”€ˆ­Ä¡˜¤¤ì”€„ô¹¥°ì($$%É•ÑÕÉ¸”($%ô(%ô(%É•ÑÕÉ¸½Ì¹I•µ½Ù•±°¡¤)ô)™Õ¹ŒÉÕ¹…Ì¡™¥±”°Á…É…µÌÍÑÉ¥¹œ¤€¡Õ¥¹ĞÌÈ°•ÉÉ½È¤ì(%Ì€èôM!11aUQ%9=í‰M¥é”èÕ¥¹ĞÌÈ¡Õ¹Í…™”¹M¥é•½˜¡M!11aUQ%9=íô¤¤°5…Í¬èM}5M-}9=1=MAI=ML°!İ¹è…ÁÀ¹¡İ¹°1ÁY•ÉˆèÔÄØ ‰ÉÕ¹…Ìˆ¤°1Á¥±”èÔÄØ¡™¥±”¤°1ÁA…É…µ•Ñ•ÉÌèÔÄØ¡Á…É…µÌ¤°9M¡½ÜèM]}!%ô(%½¬°|°”€èôÁM¡•±±á•ÕÑ•à¹…±°¡Õ¥¹ÑÁÑÈ¡Õ¹Í…™”¹A½¥¹Ñ•È ™Ì¤¤¤(%¥˜½¬€ôô€Àì($%É•ÑÕÉ¸€À°”(%ô(%‘•™•ÈÁ±½Í•!…¹‘±”¹…±°¡Õ¥¹ÑÁÑÈ¡Ì¹!AÉ½•ÍÌ¤¤(%Á]…¥Ğ¹…±°¡Õ¥¹ÑÁÑÈ¡Ì¹!AÉ½•ÍÌ¤°€Áá™™™™™™™˜¤(%Ù…È½‘”Õ¥¹ĞÌÈ(%Áá¥Ñ½‘”¹…±°¡Õ¥¹ÑÁÑÈ¡Ì¹!AÉ½•ÍÌ¤°Õ¥¹ÑÁÑÈ¡Õ¹Í…™”¹A½¥¹Ñ•È ™½‘”¤¤¤(%É•ÑÕÉ¸½‘”°¹¥°)ô)™Õ¹Œ½Á•¸¡ĞÍÑÉ¥¹œ¤ì(%ÁM¡•±±á•ÕÑ”¹…±°¡Õ¥¹ÑÁÑÈ¡…ÁÀ¹¡İ¹¤°Õ¥¹ÑÁÑÈ¡Õ¹Í…™”¹A½¥¹Ñ•È¡ÔÄØ ‰½Á•¸ˆ¤¤¤°Õ¥¹ÑÁÑÈ¡Õ¹Í…™”¹A½¥¹Ñ•È¡ÔÄØ¡Ğ¤¤¤°€À°€À°M]}M!=]9=I50¤)ô)™Õ¹Œ•ÑQ•áĞ¡ ÍåÍ…±°¹!…¹‘±”¤ÍÑÉ¥¹œì(%¸°|°|€èôÁ•ÑQ•áÑ1•¸¹…±°¡Õ¥¹ÑÁÑÈ¡ ¤¤(%ˆ€èôµ…­”¡muÕ¥¹ĞÄØ°¸¬Ä¤(%Á•ÑQ•áĞ¹…±°¡Õ¥¹ÑÁÑÈ¡ ¤°Õ¥¹ÑÁÑÈ¡Õ¹Í…™”¹A½¥¹Ñ•È ™‰lÁt¤¤°¸¬Ä¤(%É•ÑÕÉ¸ÍåÍ…±°¹UQÄÙQ½MÑÉ¥¹œ¡ˆ¤)ô)™Õ¹ŒÍ•ÑQ•áĞ¡ ÍåÍ…±°¹!…¹‘±”°ÌÍÑÉ¥¹œ¤ì(%¥˜ €„ô€Àì($%ÁM•ÑQ•áĞ¹…±°¡Õ¥¹ÑÁÑÈ¡ ¤°Õ¥¹ÑÁÑÈ¡Õ¹Í…™”¹A½¥¹Ñ•È¡ÔÄØ¡Ì¤¤¤¤(%ô)ô)™Õ¹Œ¡•­•¡¥¥¹Ğ¤‰½½°ìÈ°|°|€èôÁ%Í¡•­•¹…±°¡Õ¥¹ÑÁÑÈ¡…ÁÀ¹¡İ¹¤°Õ¥¹ÑÁÑÈ¡¥¤¤ìÉ•ÑÕÉ¸È€ôô€Äô)™Õ¹Œ‰½à¡ÌÍÑÉ¥¹œ°˜Õ¥¹ĞÌÈ¤¥¹Ğì(%È°|°|€èôÁ5•ÍÍ…•	½à¹…±°¡Õ¥¹ÑÁÑÈ¡…ÁÀ¹¡İ¹¤°Õ¥¹ÑÁÑÈ¡Õ¹Í…™”¹A½¥¹Ñ•È¡ÔÄØ¡Ì¤¤¤°Õ¥¹ÑÁÑÈ¡Õ¹Í…™”¹A½¥¹Ñ•È¡ÔÄØ¡…ÁÁQ¥Ñ±”¤¤¤°Õ¥¹ÑÁÑÈ¡˜¤¤(%É•ÑÕÉ¸¥¹Ğ¡È¤)ô)™Õ¹Œ¡¥‘‘•¸ ¤€©ÍåÍ…±°¹MåÍAÉ½ÑÑÈì(%É•ÑÕÉ¸€™ÍåÍ…±°¹MåÍAÉ½ÑÑÉí!¥‘•]¥¹‘½ÜèÑÉÕ”°É•…Ñ¥½¹±…ÌèIQ}9=}]%9=]ô)ô)™Õ¹ŒÑ…¥°¡ÌÍÑÉ¥¹œ°¸¥¹Ğ¤ÍÑÉ¥¹œì(%È€èômuÉÕ¹”¡Ì¤(%¥˜±•¸¡È¤€ğô¸ì($%É•ÑÕÉ¸Ì(%ô(%É•ÑÕÉ¸ÍÑÉ¥¹œ¡Ém±•¸¡È¤µ¸ét¤)ô)™Õ¹Œ½Áå¥±”¡„°ˆÍÑÉ¥¹œ¤•ÉÉ½Èì(%¤°”€èô½Ì¹=Á•¸¡„¤(%¥˜”€„ô¹¥°ì($%É•ÑÕÉ¸”(%ô(%‘•™•È¤¹±½Í” ¤(%|€ô½Ì¹5­‘¥É±°¡™¥±•Á…Ñ ¹¥È¡ˆ¤°€ÀÜÔÔ¤(%¼°”€èô½Ì¹É•…Ñ”¡ˆ¤(%¥˜”€„ô¹¥°ì($%É•ÑÕÉ¸”(%ô(%|°”€ô¥¼¹½Áä¡¼°¤¤(%”€èô¼¹±½Í” ¤(%¥˜”€„ô¹¥°ì($%É•ÑÕÉ¸”(%ô(%É•ÑÕÉ¸”)ô)™Õ¹Œ½Áå¥È¡„°ˆÍÑÉ¥¹œ¤•ÉÉ½Èì(%É•ÑÕÉ¸™¥±•Á…Ñ ¹]…±¬¡„°™Õ¹Œ¡ÀÍÑÉ¥¹œ°¤½Ì¹¥±•%¹™¼°”•ÉÉ½È¤•ÉÉ½Èì($%¥˜”€„ô¹¥°ì($$%É•ÑÕÉ¸¹¥°($%ô($%È°|€èô™¥±•Á…Ñ ¹I•°¡„°À¤($%Ğ€èô™¥±•Á…Ñ ¹)½¥¸¡ˆ°È¤($%¥˜¤¹%Í¥È ¤ì($$%É•ÑÕÉ¸½Ì¹5­‘¥É±°¡Ğ°€ÀÜÔÔ¤($%ô($%É•ÑÕÉ¸½Áå¥±”¡À°Ğ¤(%ô¤)ô(