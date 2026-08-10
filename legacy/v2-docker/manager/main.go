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
    appTitle   = "Haya Job Autopilot Manager"
    appVersion = "2.0.2"

    WS_OVERLAPPED    = 0x00000000
    WS_CAPTION       = 0x00C00000
    WS_SYSMENU       = 0x00080000
    WS_THICKFRAME    = 0x00040000
    WS_MINIMIZEBOX   = 0x00020000
    WS_MAXIMIZEBOX   = 0x00010000
    WS_VISIBLE       = 0x10000000
    WS_CHILD         = 0x40000000
    WS_TABSTOP       = 0x00010000
    WS_BORDER        = 0x00800000
    BS_PUSHBUTTON    = 0
    BS_DEFPUSHBUTTON = 1
    BS_AUTOCHECKBOX  = 3
    BS_GROUPBOX      = 7
    SS_LEFT          = 0
    SS_NOPREFIX      = 0x80
    ES_PASSWORD      = 0x20
    ES_AUTOHSCROLL   = 0x80
    ES_NUMBER        = 0x2000
    CBS_DROPDOWNLIST = 3

    WM_CREATE        = 1
    WM_DESTROY       = 2
    WM_SIZE          = 5
    WM_COMMAND       = 0x111
    WM_GETMINMAXINFO = 0x24
    WM_CLOSE         = 0x10
    WM_SETFONT       = 0x30
    WM_APP           = 0x8000
    WM_UPDATE        = WM_APP + 10

    BN_CLICKED    = 0
    CBN_SELCHANGE = 1

    SW_SHOW       = 5
    SW_HIDE       = 0
    SW_SHOWNORMAL = 1

    MB_OK              = 0
    MB_ICONINFORMATION = 0x40
    MB_ICONWARNING     = 0x30
    MB_ICONERROR       = 0x10
    MB_YESNO           = 4
    MB_DEFBUTTON2      = 0x100
    IDYES              = 6

    SEE_MASK_NOCLOSEPROCESS = 0x40
    CREATE_NO_WINDOW        = 0x08000000
    ERROR_ALREADY_EXISTS    = 183
    IDC_ARROW               = 32512
    COLOR_WINDOW            = 5

    ID_INSTALL       = 101
    ID_CHECK         = 102
    ID_SAVE          = 103
    ID_REPLACE_CV    = 104
    ID_START         = 105
    ID_STOP          = 106
    ID_RESTART       = 107
    ID_OPEN          = 108
    ID_RUN           = 109
    ID_LOGS          = 110
    ID_FOLDER        = 111
    ID_UNINSTALL     = 112
    ID_PASS          = 201
    ID_AUTH          = 202
    ID_SPONSOR       = 203
    ID_STARTDATE     = 204
    ID_SALARY        = 205
    ID_LIMIT         = 206
    ID_AUTO          = 207
    ID_REMOVE_DOCKER = 208
    ID_REMOVE_WSL    = 209
    ID_BACKUP        = 210
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
type MINMAXINFO struct {
    PtReserved, PtMaxSize, PtMaxPosition, PtMinTrackSize, PtMaxTrackSize POINT
}
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
    status string
    detail string
    msg    string
    busy   *bool
    kind   uint32
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
func bp(v bool) *bool      { return &v }

func main() {
    if len(os.Args) > 1 && os.Args[1] == "--autostart" {
        runAutostart()
        return
    }
    runtime.LockOSThread()
    pSetDPI.Call()
    mutex, _, mutexErr := pCreateMutex.Call(0, 1, uintptr(unsafe.Pointer(u16("Local\\HayaJobAutopilotManagerSingleInstance"))))
    if mutex == 0 { panic(mutexErr) }
    app.instanceMutex = syscall.Handle(mutex)
    if errno, ok := mutexErr.(syscall.Errno); ok && errno == ERROR_ALREADY_EXISTS {
        box("Haya Job Autopilot Manager is already open. Use the existing window.", MB_OK|MB_ICONINFORMATION)
        pCloseHandle.Call(mutex)
        return
    }
    defer pCloseHandle.Call(mutex)
    app.controls = map[int]syscall.Handle{}
    app.named = map[string]syscall.Handle{}
    app.updates = make(chan update, 64)
    h, _, _ := pGetModule.Call(0)
    app.hinst = syscall.Handle(h)
    className := u16("HayaManagerV202")
    cursor, _, _ := pLoadCursor.Call(0, IDC_ARROW)
    wc := WNDCLASSEX{CbSize:uint32(unsafe.Sizeof(WNDCLASSEX{})), LpfnWndProc:syscall.NewCallback(wndProc), HInstance:app.hinst, HCursor:syscall.Handle(cursor), HbrBackground:syscall.Handle(COLOR_WINDOW+1), LpszClassName:className}
    if a, _, e := pRegisterClass.Call(uintptr(unsafe.Pointer(&wc))); a == 0 { panic(e) }
    style := uintptr(WS_OVERLAPPED|WS_CAPTION|WS_SYSMENU|WS_THICKFRAME|WS_MINIMIZEBOX|WS_MAXIMIZEBOX|WS_VISIBLE)
    hw, _, e := pCreateWindow.Call(0, uintptr(unsafe.Pointer(className)), uintptr(unsafe.Pointer(u16(appTitle+" "+appVersion))), style, ^uintptr(0x7fffffff), ^uintptr(0x7fffffff), 1240, 760, 0, 0, uintptr(app.hinst), 0)
    if hw == 0 { panic(e) }
    app.hwnd = syscall.Handle(hw)
    pShowWindow.Call(hw, SW_SHOW)
    pUpdateWindow.Call(hw)
    go refreshStatus()
    var m MSG
    for {
        r, _, _ := pGetMessage.Call(uintptr(unsafe.Pointer(&m)), 0, 0, 0)
        if int32(r) <= 0 { break }
        pTranslate.Call(uintptr(unsafe.Pointer(&m)))
        pDispatch.Call(uintptr(unsafe.Pointer(&m)))
    }
    for _, f := range []syscall.Handle{app.font, app.bold, app.title} { if f != 0 { pDeleteObject.Call(uintptr(f)) } }
}

func wndProc(hwnd uintptr, msg uint32, w, l uintptr) uintptr {
    switch msg {
    case WM_CREATE:
        app.hwnd=syscall.Handle(hwnd); createFonts(); createUI(); layout(); return 0
    case WM_SIZE:
        layout(); return 0
    case WM_GETMINMAXINFO:
        if l!=0 { m:=(*MINMAXINFO)(unsafe.Pointer(l)); m.PtMinTrackSize=POINT{X:1000,Y:650} }; return 0
    case WM_COMMAND:
        id:=int(lo(w)); n:=int(hi(w)); if n==BN_CLICKED||n==CBN_SELCHANGE { command(id) }; return 0
    case WM_UPDATE:
        drainUpdates(); return 0
    case WM_CLOSE:
        if app.busy && box("An operation is still running. Close the manager anyway?",MB_YESNO|MB_ICONWARNING|MB_DEFBUTTON2)!=IDYES { return 0 }
        r,_,_:=pDefWindow.Call(hwnd,uintptr(msg),w,l); return r
    case WM_DESTROY:
        pPostQuit.Call(0); return 0
    }
    r,_,_:=pDefWindow.Call(hwnd,uintptr(msg),w,l); return r
}

func createFonts(){app.font=createFont(17,400);app.bold=createFont(18,650);app.title=createFont(30,700)}
func createFont(h,weight int32)syscall.Handle{r,_,_:=pCreateFont.Call(uintptr(-h),0,0,0,uintptr(weight),0,0,0,1,0,0,5,0,uintptr(unsafe.Pointer(u16("Segoe UI"))));return syscall.Handle(r)}
func ctrl(class,text string,style uintptr,id int,fontH syscall.Handle)syscall.Handle{r,_,_:=pCreateWindow.Call(0,uintptr(unsafe.Pointer(u16(class))),uintptr(unsafe.Pointer(u16(text))),style|WS_CHILD|WS_VISIBLE,0,0,100,30,uintptr(app.hwnd),uintptr(id),uintptr(app.hinst),0);h:=syscall.Handle(r);if id!=0{app.controls[id]=h};pSend.Call(r,WM_SETFONT,uintptr(fontH),1);return h}
func named(name,class,text string,style uintptr,fontH syscall.Handle)syscall.Handle{h:=ctrl(class,text,style,0,fontH);app.named[name]=h;return h}
func label(name,text string,bold bool)syscall.Handle{f:=app.font;if bold{f=app.bold};return named(name,"STATIC",text,SS_LEFT|SS_NOPREFIX,f)}
func button(text string,id int,primary bool)syscall.Handle{style:=uintptr(BS_PUSHBUTTON|WS_TABSTOP);if primary{style=BS_DEFPUSHBUTTON|WS_TABSTOP};return ctrl("BUTTON",text,style,id,app.font)}
func edit(text string,id int,password,numeric bool)syscall.Handle{style:=uintptr(WS_BORDER|WS_TABSTOP|ES_AUTOHSCROLL);if password{style|=ES_PASSWORD};if numeric{style|=ES_NUMBER};return ctrl("EDIT",text,style,id,app.font)}
func checkbox(text string,id int)syscall.Handle{return ctrl("BUTTON",text,BS_AUTOCHECKBOX|WS_TABSTOP,id,app.font)}
func group(name,text string)syscall.Handle{return named(name,"BUTTON",text,BS_GROUPBOX,app.bold)}

func createUI(){
    title:=label("title","Haya Job Autopilot Manager",true);pSend.Call(uintptr(title),WM_SETFONT,uintptr(app.title),1)
    label("subtitle","One guided Windows app for install, private details, daily control, and complete removal",false)
    group("g1","Step 1 - Install / Repair")
    label("installDesc","Installs the embedded job agent, prepares WSL 2 and Docker Desktop when required, and starts everything in the background.",false)
    label("status","Status: Checking...",true);label("detail","No status result yet.",false)
    button("Install / Repair and Start",ID_INSTALL,true);button("Check status",ID_CHECK,false)
    group("g2","Step 2 - Private details")
    label("lPass","Yahoo app password",false);label("lAuth","Hungarian work authorization",false);label("lSponsor","Employer sponsorship required",false);label("lDate","Earliest start date",false);label("lSalary","Minimum gross monthly salary HUF",false);label("lLimit","Applications per day",false)
    edit("",ID_PASS,true,false);edit("",ID_AUTH,false,false)
    combo:=ctrl("COMBOBOX","",CBS_DROPDOWNLIST|WS_TABSTOP,ID_SPONSOR,app.font);pSend.Call(uintptr(combo),0x0143,0,uintptr(unsafe.Pointer(u16("Select..."))));pSend.Call(uintptr(combo),0x0143,0,uintptr(unsafe.Pointer(u16("No"))));pSend.Call(uintptr(combo),0x0143,0,uintptr(unsafe.Pointer(u16("Yes"))));pSend.Call(uintptr(combo),0x014E,0,0)
    edit("",ID_STARTDATE,false,false);edit("",ID_SALARY,false,true);edit("5",ID_LIMIT,false,true)
    checkbox("Enable strict verified corporate-email auto-apply",ID_AUTO);button("Save details and restart agent",ID_SAVE,true);button("Replace embedded CV",ID_REPLACE_CV,false);label("privateNote","Auto-apply remains off until all required values are complete and truthful.",false)
    group("g3","Step 3 - Run / Manage / Uninstall")
    label("manageDesc","Daily controls run outside the UI thread, so the window stays responsive while Docker or the agent is busy.",false)
    button("Start agent",ID_START,true);button("Stop agent",ID_STOP,false);button("Restart agent",ID_RESTART,false);button("Open dashboard",ID_OPEN,false);button("Run job check now",ID_RUN,false);button("Open logs",ID_LOGS,false);button("Open app folder",ID_FOLDER,false)
    checkbox("Back up job history before uninstall",ID_BACKUP);checkbox("Also uninstall Docker Desktop",ID_REMOVE_DOCKER);checkbox("Also disable WSL 2 features",ID_REMOVE_WSL);pCheck.Call(uintptr(app.hwnd),ID_BACKUP,1)
    button("Uninstall Haya Job Autopilot",ID_UNINSTALL,false);label("uninstallNote","Leave Docker/WSL unchecked if other applications on this PC may use them.",false)
}

func layout(){
    if app.hwnd==0{return};var rc RECT;pGetClientRect.Call(uintptr(app.hwnd),uintptr(unsafe.Pointer(&rc)));w:=int(rc.Right-rc.Left);h:=int(rc.Bottom-rc.Top);if w<=0||h<=0{return}
    margin:=18;headerH:=76;gap:=14;colW:=(w-margin*2-gap*2)/3;top:=margin+headerH;boxH:=h-top-margin
    mv(app.named["title"],margin,8,w-margin*2,38);mv(app.named["subtitle"],margin,45,w-margin*2,25)
    x1:=margin;x2:=margin+colW+gap;x3:=margin+(colW+gap)*2
    mv(app.named["g1"],x1,top,colW,boxH);mv(app.named["installDesc"],x1+16,top+34,colW-32,78);mv(app.named["status"],x1+16,top+124,colW-32,28);mv(app.named["detail"],x1+16,top+156,colW-32,120);mv(app.controls[ID_INSTALL],x1+16,top+292,colW-32,44);mv(app.controls[ID_CHECK],x1+16,top+348,colW-32,40)
    mv(app.named["g2"],x2,top,colW,boxH);y:=top+34
    row:=func(labelName string,id int,controlH int){mv(app.named[labelName],x2+16,y,colW-32,22);y+=22;mv(app.controls[id],x2+16,y,colW-32,controlH);y+=controlH+10}
    row("lPass",ID_PASS,30);row("lAuth",ID_AUTH,30);row("lSponsor",ID_SPONSOR,100);row("lDate",ID_STARTDATE,30);row("lSalary",ID_SALARY,30);row("lLimit",ID_LIMIT,30)
    mv(app.controls[ID_AUTO],x2+16,y,colW-32,34);y+=44;mv(app.controls[ID_SAVE],x2+16,y,colW-32,42);y+=52;mv(app.controls[ID_REPLACE_CV],x2+16,y,colW-32,38);y+=48;mv(app.named["privateNote"],x2+16,y,colW-32,52)
    mv(app.named["g3"],x3,top,colW,boxH);mv(app.named["manageDesc"],x3+16,top+34,colW-32,70);by:=top+116
    for _,id:=range []int{ID_START,ID_STOP,ID_RESTART,ID_OPEN,ID_RUN,ID_LOGS,ID_FOLDER}{mv(app.controls[id],x3+16,by,colW-32,36);by+=44}
    by+=10;mv(app.controls[ID_BACKUP],x3+16,by,colW-32,28);by+=34;mv(app.controls[ID_REMOVE_DOCKER],x3+16,by,colW-32,28);by+=34;mv(app.controls[ID_REMOVE_WSL],x3+16,by,colW-32,28);by+=40;mv(app.controls[ID_UNINSTALL],x3+16,by,colW-32,40);by+=50;mv(app.named["uninstallNote"],x3+16,by,colW-32,54)
}
func mv(h syscall.Handle,x,y,w,hgt int){if h==0{return};if w<1{w=1};if hgt<1{hgt=1};pMoveWindow.Call(uintptr(h),uintptr(x),uintptr(y),uintptr(w),uintptr(hgt),1)}

func command(id int){
    if app.busy&&id!=ID_OPEN&&id!=ID_LOGS&&id!=ID_FOLDER{return}
    switch id{
    case ID_INSTALL:startInstall()
    case ID_CHECK:go refreshStatus()
    case ID_SAVE:busy("Saving private details...",savePrivate)
    case ID_REPLACE_CV:go replaceCV()
    case ID_START:busy("Starting agent...",func()error{return compose("up","-d")})
    case ID_STOP:busy("Stopping agent...",func()error{return compose("stop")})
    case ID_RESTART:busy("Restarting agent...",func()error{return compose("restart")})
    case ID_OPEN:open("http://127.0.0.1:8787")
    case ID_RUN:busy("Running job check...",runCheck)
    case ID_LOGS:_=os.MkdirAll(filepath.Join(installDir(),"logs"),0755);open(filepath.Join(installDir(),"logs"))
    case ID_FOLDER:open(installDir())
    case ID_UNINSTALL:uninstallPrompt()
    }
}

func startInstall(){
    if !app.installing.CompareAndSwap(false,true){post(update{status:"Working",detail:"Installation is already running. No additional administrator prompt will be opened."});return}
    setBusy(true);post(update{status:"Working",detail:"Installing / repairing in the background. Windows may show one administrator approval prompt."})
    go func(){defer app.installing.Store(false);if e:=install();e!=nil{post(update{status:"Install paused",detail:e.Error(),busy:bp(false)});return};post(update{status:"Ready",detail:"Installation completed successfully.",busy:bp(false)});go refreshStatus()}()
}
func busy(detail string,fn func()error){setBusy(true);post(update{status:"Working",detail:detail});go func(){if e:=fn();e!=nil{post(update{status:"Error",detail:e.Error(),msg:e.Error(),kind:MB_ICONERROR,busy:bp(false)})}else{post(update{status:"Ready",detail:"Operation completed successfully.",busy:bp(false)});go refreshStatus()}}()}
func setBusy(v bool){app.busy=v;enabled:=uintptr(1);if v{enabled=0};for _,id:=range []int{ID_INSTALL,ID_CHECK,ID_SAVE,ID_REPLACE_CV,ID_START,ID_STOP,ID_RESTART,ID_RUN,ID_UNINSTALL}{if h:=app.controls[id];h!=0{pEnable.Call(uintptr(h),enabled)}}}
func post(u update){select{case app.updates<-u:default:<-app.updates;app.updates<-u};pPost.Call(uintptr(app.hwnd),WM_UPDATE,0,0)}
func drainUpdates(){for{select{case u:=<-app.updates:if u.status!=""{setText(app.named["status"],"Status: "+u.status)};if u.detail!=""{setText(app.named["detail"],u.detail)};if u.busy!=nil{setBusy(*u.busy)};if u.msg!=""{box(u.msg,MB_OK|u.kind)};default:return}}}
func refreshStatus(){status,detail:=probeStatus();post(update{status:status,detail:detail})}

func probeStatus()(string,string){
    if _,e:=os.Stat(filepath.Join(installDir(),"docker-compose.yml"));e!=nil{return "Not installed","Click Install / Repair and Start. The installer will unpack the embedded agent and prepare Docker/WSL if needed."}
    docker,e:=findDocker();if e!=nil{return "Agent files installed","Docker Desktop is not installed yet. Click Install / Repair and Start."}
    ctx,cancel:=context.WithTimeout(context.Background(),4*time.Second);defer cancel();cmd:=exec.CommandContext(ctx,docker,"info");cmd.SysProcAttr=hidden();if e=cmd.Run();e!=nil{if ctx.Err()==context.DeadlineExceeded{return "Docker starting","Docker status check timed out without blocking the manager UI."};return "Docker not ready","Docker Desktop is installed but its engine is not running."}
    ctx2,cancel2:=context.WithTimeout(context.Background(),5*time.Second);defer cancel2();cmd2:=exec.CommandContext(ctx2,docker,"compose","-f",filepath.Join(installDir(),"docker-compose.yml"),"ps","--status","running","--quiet");cmd2.Dir=installDir();cmd2.SysProcAttr=hidden();out,e:=cmd2.Output();if e==nil&&strings.TrimSpace(string(out))!=""{return "Running","The agent container is online. Dashboard: http://127.0.0.1:8787"};return "Installed but stopped","Docker is ready, but the Haya Job Autopilot container is not running."
}

func installDir()string{base:=os.Getenv("LOCALAPPDATA");if base==""{base=filepath.Join(os.Getenv("USERPROFILE"),"AppData","Local")};return filepath.Join(base,"HayaJobAutopilot")}
func install()error{if e:=extractPayload();e!=nil{return fmt.Errorf("extract embedded agent: %w",e)};if e:=copySelf();e!=nil{return fmt.Errorf("install manager copy: %w",e)};if e:=ensureEnv();e!=nil{return fmt.Errorf("prepare local settings: %w",e)};if _,e:=findDocker();e!=nil{if e=installDocker();e!=nil{return e}};if e:=waitDocker(6*time.Minute);e!=nil{return e};if e:=composeTimeout(20*time.Minute,"up","-d","--build");e!=nil{return e};if e:=autostart();e!=nil{return fmt.Errorf("configure automatic startup: %w",e)};open("http://127.0.0.1:8787");return nil}

func extractPayload()error{dir:=installDir();if e:=os.MkdirAll(dir,0755);e!=nil{return e};zr,e:=zip.NewReader(bytes.NewReader(payload),int64(len(payload)));if e!=nil{return e};for _,f:=range zr.File{clean:=filepath.Clean(f.Name);if clean=="."||filepath.IsAbs(clean)||strings.HasPrefix(clean,".."){return fmt.Errorf("unsafe embedded path: %s",f.Name)};target:=filepath.Join(dir,clean);if f.FileInfo().IsDir(){if e=os.MkdirAll(target,0755);e!=nil{return e};continue};base:=filepath.Base(target);if _,statErr:=os.Stat(target);statErr==nil{if base==".env"||strings.HasSuffix(target,string(os.PathSeparator)+"jobs.json")||strings.HasSuffix(target,string(os.PathSeparator)+"state.json"){continue}};if e=os.MkdirAll(filepath.Dir(target),0755);e!=nil{return e};rc,openErr:=f.Open();if openErr!=nil{return openErr};data,readErr:=io.ReadAll(io.LimitReader(rc,50<<20));_=rc.Close();if readErr!=nil{return readErr};if e=os.WriteFile(target,data,0644);e!=nil{return e}};return nil}
func copySelf()error{src,e:=os.Executable();if e!=nil{return e};dst:=filepath.Join(installDir(),"Haya_Job_Autopilot_Manager.exe");if strings.EqualFold(filepath.Clean(src),filepath.Clean(dst)){return nil};return copyFile(src,dst)}
func ensureEnv()error{dst:=filepath.Join(installDir(),".env");if _,e:=os.Stat(dst);e==nil{return nil};data,e:=os.ReadFile(filepath.Join(installDir(),".env.example"));if e!=nil{return e};return os.WriteFile(dst,data,0600)}

func acquireDockerInstallLock()(func(),error){lock:=filepath.Join(os.TempDir(),"HayaJobAutopilot-DockerInstall.lock");if info,e:=os.Stat(lock);e==nil{if time.Since(info.ModTime())<30*time.Minute{return nil,errors.New("Docker/WSL installation is already running. Wait for it to finish; no new administrator prompt was opened")};_=os.Remove(lock)};f,e:=os.OpenFile(lock,os.O_WRONLY|os.O_CREATE|os.O_EXCL,0600);if e!=nil{if os.IsExist(e){return nil,errors.New("Docker/WSL installation is already running. No additional administrator prompt was opened")};return nil,fmt.Errorf("create installer lock: %w",e)};_,_=fmt.Fprintf(f,"pid=%d\nstarted=%s\n",os.Getpid(),time.Now().Format(time.RFC3339));_=f.Close();return func(){_=os.Remove(lock)},nil}

func installDocker()error{
    release,e:=acquireDockerInstallLock();if e!=nil{return e};defer release();script:=filepath.Join(installDir(),"install_docker.ps1");status:=filepath.Join(installDir(),"docker_install_status.txt");logPath:=filepath.Join(installDir(),"logs","docker_install.log");_=os.MkdirAll(filepath.Dir(logPath),0755);_=os.Remove(status)
    ps:=fmt.Sprintf(`$ErrorActionPreference='Stop';$ProgressPreference='SilentlyContinue';$s=%s;$log=%s;function L([string]$m){Add-Content -LiteralPath $log -Value ((Get-Date -Format s)+' '+$m)};try{L 'Docker/WSL setup started';$restart=$false;$a=Get-WindowsOptionalFeature -Online -FeatureName Microsoft-Windows-Subsystem-Linux;$b=Get-WindowsOptionalFeature -Online -FeatureName VirtualMachinePlatform;if($a.State -ne 'Enabled'){$x=Enable-WindowsOptionalFeature -Online -FeatureName Microsoft-Windows-Subsystem-Linux -All -NoRestart;if($x.RestartNeeded){$restart=$true}};if($b.State -ne 'Enabled'){$x=Enable-WindowsOptionalFeature -Online -FeatureName VirtualMachinePlatform -All -NoRestart;if($x.RestartNeeded){$restart=$true}};if($restart){Set-Content -LiteralPath $s -Value 'RESTART_REQUIRED';L 'Windows restart required after WSL feature enable';exit 3010};try{wsl.exe --install --no-distribution --web-download 2>&1|Add-Content -LiteralPath $log}catch{};try{wsl.exe --update --web-download 2>&1|Add-Content -LiteralPath $log}catch{};try{wsl.exe --set-default-version 2 2>&1|Add-Content -LiteralPath $log}catch{};$i=Join-Path $env:TEMP 'Docker Desktop Installer.exe';if(-not(Test-Path -LiteralPath $i)){Invoke-WebRequest -UseBasicParsing 'https://desktop.docker.com/win/main/amd64/Docker%%20Desktop%%20Installer.exe' -OutFile $i};$sig=Get-AuthenticodeSignature -LiteralPath $i;if($sig.Status -ne 'Valid' -or $sig.SignerCertificate.Subject -notmatch 'Docker'){throw 'Docker installer signature validation failed'};$p=Start-Process -FilePath $i -ArgumentList @('install','--user','--quiet','--accept-license','--backend=wsl-2','--no-windows-containers') -Wait -PassThru;L ('Docker installer exit '+$p.ExitCode);if($p.ExitCode -eq 3010){Set-Content -LiteralPath $s -Value 'RESTART_REQUIRED';exit 3010};if($p.ExitCode -ne 0){throw ('Docker installer exit '+$p.ExitCode)};Set-Content -LiteralPath $s -Value 'SUCCESS';exit 0}catch{L ('ERROR '+$_.Exception.Message);Set-Content -LiteralPath $s -Value ('ERROR: '+$_.Exception.Message);exit 1}`,psq(status),psq(logPath))
    if e=os.WriteFile(script,[]byte("\xEF\xBB\xBF"+ps),0644);e!=nil{return e};code,e:=runas("powershell.exe","-NoLogo -NoProfile -ExecutionPolicy Bypass -File "+q(script));if e!=nil{return fmt.Errorf("administrator approval or Docker setup failed: %w",e)};data,_:=os.ReadFile(status);st:=strings.TrimSpace(string(data));if code==3010||st=="RESTART_REQUIRED"{return errors.New("Windows restart is required to finish WSL 2. Restart Windows, reopen this manager, then click Install / Repair and Start again")};if code!=0||st!="SUCCESS"{if st==""{st=fmt.Sprintf("installer exit code %d",code)};return fmt.Errorf("Docker setup failed: %s",st)};return nil
}
func psq(s string)string{return "'"+strings.ReplaceAll(s,"'","''")+"'"}
func q(s string)string{return `"`+strings.ReplaceAll(s,`"`,`\"`)+`"`}
func findDocker()(string,error){candidates:=[]string{filepath.Join(os.Getenv("LOCALAPPDATA"),"Programs","DockerDesktop","resources","bin","docker.exe"),filepath.Join(os.Getenv("ProgramFiles"),"Docker","Docker","resources","bin","docker.exe")};if p,e:=exec.LookPath("docker.exe");e==nil{candidates=append([]string{p},candidates...)};for _,p:=range candidates{if p!=""{if _,e:=os.Stat(p);e==nil{return p,nil}}};return "",errors.New("docker.exe not found")}
func startDockerDesktop(){candidates:=[]string{filepath.Join(os.Getenv("LOCALAPPDATA"),"Programs","DockerDesktop","Docker Desktop.exe"),filepath.Join(os.Getenv("ProgramFiles"),"Docker","Docker","Docker Desktop.exe")};for _,p:=range candidates{if _,e:=os.Stat(p);e==nil{_=exec.Command(p).Start();return}}}
func waitDocker(t time.Duration)error{startDockerDesktop();end:=time.Now().Add(t);for time.Now().Before(end){docker,e:=findDocker();if e==nil{ctx,cancel:=context.WithTimeout(context.Background(),5*time.Second);cmd:=exec.CommandContext(ctx,docker,"info");cmd.SysProcAttr=hidden();e=cmd.Run();cancel();if e==nil{return nil}};time.Sleep(3*time.Second)};return errors.New("Docker Desktop did not become ready within six minutes")}
func compose(args ...string)error{return composeTimeout(10*time.Minute,args...)}
func composeTimeout(t time.Duration,args ...string)error{docker,e:=findDocker();if e!=nil{return e};all:=append([]string{"compose","-f",filepath.Join(installDir(),"docker-compose.yml")},args...);ctx,cancel:=context.WithTimeout(context.Background(),t);defer cancel();cmd:=exec.CommandContext(ctx,docker,all...);cmd.Dir=installDir();cmd.SysProcAttr=hidden();out,e:=cmd.CombinedOutput();_=os.MkdirAll(filepath.Join(installDir(),"logs"),0755);_=os.WriteFile(filepath.Join(installDir(),"logs","manager_last_docker.log"),out,0644);if ctx.Err()==context.DeadlineExceeded{return errors.New("Docker command timed out; open Logs")};if e!=nil{return fmt.Errorf("Docker command failed: %v\n%s",e,tail(string(out),900))};return nil}

func savePrivate()error{pass:=getText(app.controls[ID_PASS]);auth:=getText(app.controls[ID_AUTH]);sponsorIndex,_,_:=pSend.Call(uintptr(app.controls[ID_SPONSOR]),0x0147,0,0);sponsor:="";if sponsorIndex==1{sponsor="No"}else if sponsorIndex==2{sponsor="Yes"};startDate:=getText(app.controls[ID_STARTDATE]);salary:=getText(app.controls[ID_SALARY]);limit,e:=strconv.Atoi(strings.TrimSpace(getText(app.controls[ID_LIMIT])));if e!=nil||limit<1||limit>20{return errors.New("Applications per day must be a whole number from 1 to 20")};auto:=checked(ID_AUTO);if auto&&(pass==""||auth==""||sponsor==""||startDate==""){return errors.New("Auto-apply needs Yahoo app password, work authorization, sponsorship answer, and earliest start date")};if e=extractPayload();e!=nil{return e};env:=[]string{`HAYA_EMAIL="saadehhaya@yahoo.com"`,`IMAP_HOST="imap.mail.yahoo.com"`,`IMAP_PORT=993`,`SMTP_HOST="smtp.mail.yahoo.com"`,`SMTP_PORT=465`,`MAIL_APP_PASSWORD=`+envq(pass),`SCAN_SCHEDULE="0 */6 * * *"`,`MIN_MATCH_SCORE=80`,`MAX_FRAUD_RISK=30`,`MAX_WEEKLY_HOURS=30`,`DAILY_APPLICATION_LIMIT=`+strconv.Itoa(limit),`AUTO_EMAIL_APPLY=`+strconv.FormatBool(auto),`WORK_AUTHORIZATION_HUNGARY=`+envq(auth),`SPONSORSHIP_REQUIRED=`+envq(sponsor),`EARLIEST_START_DATE=`+envq(startDate),`MIN_GROSS_MONTHLY_SALARY_HUF=`+envq(salary),`CV_PATH="/app/documents/Haya_Saadeh_CV.pdf"`};if e=os.WriteFile(filepath.Join(installDir(),".env"),[]byte(strings.Join(env,"\r\n")+"\r\n"),0600);e!=nil{return e};if _,e=findDocker();e==nil{return compose("up","-d","--force-recreate")};return nil}
func envq(s string)string{s=strings.ReplaceAll(s,"\\","\\\\");s=strings.ReplaceAll(s,`"`,`\"`);s=strings.ReplaceAll(s,"\r"," ");s=strings.ReplaceAll(s,"\n"," ");return `"`+strings.TrimSpace(s)+`"`}
func replaceCV(){script:=`Add-Type -AssemblyName System.Windows.Forms;$d=New-Object System.Windows.Forms.OpenFileDialog;$d.Filter='PDF files (*.pdf)|*.pdf';if($d.ShowDialog() -eq 'OK'){[Console]::Write($d.FileName)}`;ctx,cancel:=context.WithTimeout(context.Background(),2*time.Minute);defer cancel();cmd:=exec.CommandContext(ctx,"powershell.exe","-NoLogo","-NoProfile","-STA","-Command",script);cmd.SysProcAttr=hidden();out,e:=cmd.Output();if e!=nil{return};src:=strings.TrimSpace(string(out));if src==""{return};_=os.MkdirAll(filepath.Join(installDir(),"documents"),0755);if e=copyFile(src,filepath.Join(installDir(),"documents","Haya_Saadeh_CV.pdf"));e!=nil{post(update{msg:e.Error(),kind:MB_ICONERROR})}else{post(update{msg:"CV replaced successfully.",kind:MB_ICONINFORMATION})}}
func runCheck()error{client:=http.Client{Timeout:2*time.Minute};r,e:=client.Post("http://127.0.0.1:8787/api/run","application/json",strings.NewReader("{}"));if e!=nil{return e};defer r.Body.Close();body,_:=io.ReadAll(io.LimitReader(r.Body,1<<20));if r.StatusCode<200||r.StatusCode>=300{return fmt.Errorf("Agent HTTP %d: %s",r.StatusCode,tail(string(body),700))};return nil}
func autostart()error{exe:=filepath.Join(installDir(),"Haya_Job_Autopilot_Manager.exe");value:=q(exe)+" --autostart";cmd:=exec.Command("reg.exe","add",`HKCU\Software\Microsoft\Windows\CurrentVersion\Run`,`/v`,`HayaJobAutopilot`,`/t`,`REG_SZ`,`/d`,value,`/f`);cmd.SysProcAttr=hidden();return cmd.Run()}
func runAutostart(){if _,e:=os.Stat(filepath.Join(installDir(),"docker-compose.yml"));e!=nil{return};if e:=waitDocker(5*time.Minute);e!=nil{return};_=composeTimeout(5*time.Minute,"up","-d")}

func uninstallPrompt(){message:="Remove Haya Job Autopilot local files and startup entry?";if checked(ID_REMOVE_DOCKER){message+="\n\nDocker Desktop will also be uninstalled. This can affect other applications using Docker."};if checked(ID_REMOVE_WSL){message+="\n\nWSL 2 Windows features will also be disabled."};if box(message,MB_YESNO|MB_ICONWARNING|MB_DEFBUTTON2)!=IDYES{return};busy("Uninstalling...",uninstall)}
func uninstall()error{dir:=installDir();if checked(ID_BACKUP){backup:=filepath.Join(os.Getenv("USERPROFILE"),"Desktop","Haya Job Autopilot Backup "+time.Now().Format("20060102_150405"));_=copyDir(filepath.Join(dir,"data"),filepath.Join(backup,"data"));_=copyDir(filepath.Join(dir,"applications"),filepath.Join(backup,"applications"));_=copyDir(filepath.Join(dir,"logs"),filepath.Join(backup,"logs"))};_=composeTimeout(3*time.Minute,"down");cmd:=exec.Command("reg.exe","delete",`HKCU\Software\Microsoft\Windows\CurrentVersion\Run`,`/v`,`HayaJobAutopilot`,`/f`);cmd.SysProcAttr=hidden();_=cmd.Run();if checked(ID_REMOVE_DOCKER)||checked(ID_REMOVE_WSL){ps:=[]string{"$ErrorActionPreference='Continue'"};if checked(ID_REMOVE_DOCKER){ps=append(ps,`$c=@("$env:LOCALAPPDATA\Programs\DockerDesktop\Docker Desktop Installer.exe","$env:ProgramFiles\Docker\Docker\Docker Desktop Installer.exe");foreach($i in $c){if(Test-Path $i){Start-Process $i -ArgumentList 'uninstall','--quiet' -Wait;break}}`)};if checked(ID_REMOVE_WSL){ps=append(ps,`Disable-WindowsOptionalFeature -Online -FeatureName VirtualMachinePlatform -NoRestart`,`Disable-WindowsOptionalFeature -Online -FeatureName Microsoft-Windows-Subsystem-Linux -NoRestart`)};script:=filepath.Join(os.TempDir(),"haya_uninstall.ps1");_=os.WriteFile(script,[]byte("\xEF\xBB\xBF"+strings.Join(ps,"\r\n")),0644);if _,e:=runas("powershell.exe","-NoLogo -NoProfile -ExecutionPolicy Bypass -File "+q(script));e!=nil{return e}};current,_:=os.Executable();if strings.HasPrefix(strings.ToLower(filepath.Clean(current)),strings.ToLower(filepath.Clean(dir))){remover:=filepath.Join(os.TempDir(),"remove_haya_job_autopilot.cmd");script:=fmt.Sprintf("@echo off\r\ntimeout /t 3 /nobreak >nul\r\nrmdir /s /q %s\r\ndel /q \"%%~f0\"\r\n",q(dir));_=os.WriteFile(remover,[]byte(script),0644);c:=exec.Command("cmd.exe","/c","start","","/min",remover);c.SysProcAttr=hidden();_=c.Start()}else{_=os.RemoveAll(dir)};return nil}

func runas(file,params string)(uint32,error){s:=SHELLEXECUTEINFO{CbSize:uint32(unsafe.Sizeof(SHELLEXECUTEINFO{})),FMask:SEE_MASK_NOCLOSEPROCESS,Hwnd:app.hwnd,LpVerb:u16("runas"),LpFile:u16(file),LpParameters:u16(params),NShow:SW_HIDE};ok,_,e:=pShellExecuteEx.Call(uintptr(unsafe.Pointer(&s)));if ok==0{return 0,e};defer pCloseHandle.Call(uintptr(s.HProcess));pWait.Call(uintptr(s.HProcess),0xffffffff);var code uint32;pExitCode.Call(uintptr(s.HProcess),uintptr(unsafe.Pointer(&code)));return code,nil}
func open(target string){pShellExecute.Call(uintptr(app.hwnd),uintptr(unsafe.Pointer(u16("open"))),uintptr(unsafe.Pointer(u16(target))),0,0,SW_SHOWNORMAL)}
func getText(h syscall.Handle)string{n,_,_:=pGetTextLen.Call(uintptr(h));b:=make([]uint16,n+1);pGetText.Call(uintptr(h),uintptr(unsafe.Pointer(&b[0])),n+1);return syscall.UTF16ToString(b)}
func setText(h syscall.Handle,s string){if h!=0{pSetText.Call(uintptr(h),uintptr(unsafe.Pointer(u16(s))))}}
func checked(id int)bool{r,_,_:=pIsChecked.Call(uintptr(app.hwnd),uintptr(id));return r==1}
func box(s string,flags uint32)int{r,_,_:=pMessageBox.Call(uintptr(app.hwnd),uintptr(unsafe.Pointer(u16(s))),uintptr(unsafe.Pointer(u16(appTitle))),uintptr(flags));return int(r)}
func hidden()*syscall.SysProcAttr{return &syscall.SysProcAttr{HideWindow:true,CreationFlags:CREATE_NO_WINDOW}}
func tail(s string,n int)string{r:=[]rune(s);if len(r)<=n{return s};return string(r[len(r)-n:])}
func copyFile(src,dst string)error{in,e:=os.Open(src);if e!=nil{return e};defer in.Close();if e=os.MkdirAll(filepath.Dir(dst),0755);e!=nil{return e};out,e:=os.Create(dst);if e!=nil{return e};_,copyErr:=io.Copy(out,in);closeErr:=out.Close();if copyErr!=nil{return copyErr};return closeErr}
func copyDir(src,dst string)error{if _,e:=os.Stat(src);e!=nil{return nil};return filepath.Walk(src,func(p string,info os.FileInfo,e error)error{if e!=nil{return nil};rel,_:=filepath.Rel(src,p);target:=filepath.Join(dst,rel);if info.IsDir(){return os.MkdirAll(target,0755)};return copyFile(p,target)})}
