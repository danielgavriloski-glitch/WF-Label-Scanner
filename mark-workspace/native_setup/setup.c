/*
 * MarK native Windows GUI installer / launcher, experimental v0.5.
 * No PowerShell, VBS, policy overrides, antivirus exclusions, registry edits
 * or admin elevation.  Requires Windows 10/11 x64, Internet, free disk space.
 * Designed as a transparent bootstrapper: EXE embeds source-only ZIP payload,
 * downloads official Python installer (WinVerifyTrust verified), then invokes
 * a documented Python installation worker from the bundle.
 *
 * Source built with clang --target=x86_64-pc-windows-msvc + lld-link.
 * No C runtime necessary.  All function imports resolve via OS DLLs.
 */

typedef unsigned short wchar_t;
typedef unsigned long DWORD;
typedef unsigned int UINT;
typedef int BOOL;
typedef long LONG;
typedef unsigned long long ULONGLONG;
typedef unsigned long long WPARAM;
typedef long long LPARAM;
typedef long long LRESULT;
typedef void* HANDLE;
typedef void* HWND;
typedef void* HINSTANCE;
typedef void* HBRUSH;
typedef void* HICON;
typedef void* HCURSOR;
typedef const wchar_t* LPCWSTR;
typedef wchar_t* LPWSTR;
typedef struct { LONG x, y; } POINT;
typedef struct { HWND hwnd; UINT message; WPARAM wParam; LPARAM lParam; DWORD time; POINT pt; DWORD lPrivate; } MSG;
typedef LRESULT (__stdcall *WNDPROC)(HWND, UINT, WPARAM, LPARAM);
typedef struct {
    UINT style; WNDPROC lpfnWndProc; int cbClsExtra, cbWndExtra; HINSTANCE hInstance;
    HICON hIcon; HCURSOR hCursor; HBRUSH hbrBackground;
    LPCWSTR lpszMenuName; LPCWSTR lpszClassName;
} WNDCLASSW;
typedef struct {HWND hwndOwner; void* pidlRoot; LPWSTR pszDisplayName; LPCWSTR lpszTitle; UINT ulFlags; void* lpfn; LPARAM lParam; int iImage;} BROWSEINFOW;
typedef struct {DWORD cb; LPWSTR reserved; DWORD flags;} STARTMIN;
typedef struct {
    DWORD cb; LPWSTR lpReserved; LPWSTR lpDesktop; LPWSTR lpTitle;
    DWORD dwX, dwY, dwXSize, dwYSize, dwXCountChars, dwYCountChars, dwFillAttribute, dwFlags;
    unsigned short wShowWindow, cbReserved2; unsigned char *lpReserved2; HANDLE hStdInput, hStdOutput, hStdError;
} STARTUPINFOW;
typedef struct { HANDLE hProcess, hThread; DWORD dwProcessId, dwThreadId; } PROCESS_INFORMATION;
typedef struct {DWORD Data1; unsigned short Data2,Data3; unsigned char Data4[8];} GUID;
typedef struct {DWORD cbStruct; const wchar_t* pcwszFilePath; HANDLE hFile; GUID* pgKnownSubject;} WINTRUST_FILE_INFO;
typedef struct {
    DWORD cbStruct; void* pPolicyCallbackData; void* pSIPClientData;
    DWORD dwUIChoice, fdwRevocationChecks, dwUnionChoice;
    WINTRUST_FILE_INFO* pFile; DWORD dwStateAction;
    HANDLE hWVTStateData; wchar_t* pwszURLReference;
    DWORD dwProvFlags, dwUIContext; void* pSignatureSettings;
} WINTRUST_DATA;

#define IMP __declspec(dllimport)
#define WINAPI __stdcall
IMP HINSTANCE WINAPI GetModuleHandleW(LPCWSTR);
IMP DWORD WINAPI GetModuleFileNameW(HINSTANCE,LPWSTR,DWORD);
IMP DWORD WINAPI GetEnvironmentVariableW(LPCWSTR,LPWSTR,DWORD);
IMP DWORD WINAPI GetDriveTypeW(LPCWSTR);
IMP DWORD WINAPI GetFileAttributesW(LPCWSTR);
IMP BOOL WINAPI CreateDirectoryW(LPCWSTR,void*);
IMP HANDLE WINAPI CreateFileW(LPCWSTR,DWORD,DWORD,void*,DWORD,DWORD,HANDLE);
IMP BOOL WINAPI GetFileSizeEx(HANDLE,long long*);
IMP BOOL WINAPI SetFilePointerEx(HANDLE,long long,long long*,DWORD);
IMP BOOL WINAPI ReadFile(HANDLE,void*,DWORD,DWORD*,void*);
IMP BOOL WINAPI WriteFile(HANDLE,const void*,DWORD,DWORD*,void*);
IMP BOOL WINAPI CloseHandle(HANDLE);
IMP BOOL WINAPI DeleteFileW(LPCWSTR);
IMP BOOL WINAPI CopyFileW(LPCWSTR,LPCWSTR,BOOL);
IMP BOOL WINAPI CreateProcessW(LPCWSTR,LPWSTR,void*,void*,BOOL,DWORD,void*,LPCWSTR,STARTUPINFOW*,PROCESS_INFORMATION*);
IMP DWORD WINAPI WaitForSingleObject(HANDLE,DWORD);
IMP BOOL WINAPI GetExitCodeProcess(HANDLE,DWORD*);
IMP DWORD WINAPI GetLastError(void);
IMP void WINAPI ExitProcess(UINT);
IMP HANDLE WINAPI GetStdHandle(DWORD);
IMP BOOL WINAPI WriteConsoleW(HANDLE,LPCWSTR,DWORD,DWORD*,void*);
IMP BOOL WINAPI AllocConsole(void);
IMP BOOL WINAPI SetConsoleTitleW(LPCWSTR);
IMP BOOL WINAPI GetDiskFreeSpaceExW(LPCWSTR,unsigned long long*,unsigned long long*,unsigned long long*);
IMP LPWSTR WINAPI GetCommandLineW(void);
IMP unsigned short WINAPI RegisterClassW(const WNDCLASSW*);
IMP HWND WINAPI CreateWindowExW(DWORD,LPCWSTR,LPCWSTR,DWORD,int,int,int,int,HWND,HANDLE,HINSTANCE,void*);
IMP LRESULT WINAPI DefWindowProcW(HWND,UINT,WPARAM,LPARAM);
IMP BOOL WINAPI ShowWindow(HWND,int);
IMP BOOL WINAPI UpdateWindow(HWND);
IMP BOOL WINAPI GetMessageW(MSG*,HWND,UINT,UINT);
IMP BOOL WINAPI TranslateMessage(const MSG*);
IMP LRESULT WINAPI DispatchMessageW(const MSG*);
IMP void WINAPI PostQuitMessage(int);
IMP int WINAPI MessageBoxW(HWND,LPCWSTR,LPCWSTR,UINT);
IMP BOOL WINAPI SetWindowTextW(HWND,LPCWSTR);
IMP int WINAPI GetWindowTextW(HWND,LPWSTR,int);
IMP BOOL WINAPI EnableWindow(HWND,BOOL);
IMP UINT WINAPI SetTimer(HWND,UINT,UINT,void*);
IMP BOOL WINAPI KillTimer(HWND,UINT);
IMP BOOL WINAPI DestroyWindow(HWND);
IMP HANDLE WINAPI LoadCursorW(HINSTANCE,LPCWSTR);
IMP HICON WINAPI LoadIconW(HINSTANCE,LPCWSTR);
IMP void* WINAPI SHBrowseForFolderW(const BROWSEINFOW*);
IMP BOOL WINAPI SHGetPathFromIDListW(void*,LPWSTR);
IMP void WINAPI CoTaskMemFree(void*);
IMP LONG WINAPI WinVerifyTrust(HWND,GUID*,WINTRUST_DATA*);

#define GENERIC_READ 0x80000000UL
#define GENERIC_WRITE 0x40000000UL
#define FILE_SHARE_READ 1
#define OPEN_EXISTING 3
#define CREATE_ALWAYS 2
#define FILE_ATTRIBUTE_NORMAL 128
#define INVALID_FILE_ATTRIBUTES 0xffffffffUL
#define FILE_BEGIN 0
#define INFINITE 0xffffffffUL
#define STILL_ACTIVE 259
#define WS_VISIBLE 0x10000000UL
#define WS_CHILD 0x40000000UL
#define WS_OVERLAPPEDWINDOW 0x00CF0000UL
#define WS_BORDER 0x00800000UL
#define WS_TABSTOP 0x00010000UL
#define ES_AUTOHSCROLL 0x80UL
#define BS_DEFPUSHBUTTON 1UL
#define WM_CREATE 1
#define WM_DESTROY 2
#define WM_COMMAND 0x111
#define WM_TIMER 0x113
#define WM_CLOSE 0x10
#define SW_SHOW 5
#define DRIVE_FIXED 3
#define COLOR_WINDOW 5
#define MB_ICONWARNING 0x30
#define MB_ICONINFORMATION 0x40

static HINSTANCE g_hinst;
static HWND g_main, g_edit, g_install, g_repair, g_launch, g_status;
static HANDLE g_worker = 0;
static wchar_t g_install_path[1024];
static wchar_t g_self[2048];
static wchar_t g_target[1024];
static DWORD g_worker_code=259;
static int g_action=0; /* 1=new installation, 2=non-destructive FFmpeg repair */
static wchar_t g_buf[8192];
static wchar_t g_pathbuf[4096];
static unsigned char g_io[65536];
static const wchar_t *g_cls = L"MarKNativeSetupWindow";

static int slen(const wchar_t *s){int i=0;if(!s)return 0; while(s[i] && i<16000)i++;return i;}
static void scopy(wchar_t *d,int cap,const wchar_t*s){int i=0;if(!s){if(cap)d[0]=0;return;} while(s[i] && i<cap-1){d[i]=s[i];i++;}if(cap)d[i]=0;}
static void scat(wchar_t*d,int cap,const wchar_t*s){int i=slen(d),j=0;if(!s)return; while(s[j] && i<cap-1) d[i++]=s[j++]; d[i]=0;}
static int seq(const wchar_t*a,const wchar_t*b){int i=0;while(a[i] && b[i] && a[i]==b[i])i++;return a[i]==0&&b[i]==0;}
static int contains(const wchar_t*s,const wchar_t*part){int i,j; for(i=0;s[i];i++){j=0;while(part[j]&&s[i+j]&&s[i+j]==part[j])j++; if(!part[j])return i;}return -1;}
static void self_root(wchar_t*out,int capacity){scopy(out,capacity,g_self);for(int i=slen(out)-1;i>=0;i--){if(out[i]==L'\\'||out[i]==L'/'){out[i]=0;break;}}}
static void joined(wchar_t*out,int cap,const wchar_t*folder,const wchar_t*child){scopy(out,cap,folder);if(slen(out)>0 && out[slen(out)-1]!=L'\\')scat(out,cap,L"\\");scat(out,cap,child);}
static void worker_print(const wchar_t*str){DWORD n=0; HANDLE h=GetStdHandle(0xfffffff5UL);if(h && h!=(HANDLE)-1){WriteConsoleW(h,str,(DWORD)slen(str),&n,0);WriteConsoleW(h,L"\r\n",2,&n,0);}}
static int file_exists(const wchar_t*path){return GetFileAttributesW(path)!=INVALID_FILE_ATTRIBUTES;}
static int make_dirs(const wchar_t*path){
    wchar_t temp[2048];scopy(temp,2048,path);
    int n=slen(temp); if(n<4 || n>900)return 0;
    for(int i=3;i<n;i++)if(temp[i]==L'\\'||temp[i]==L'/'){
        wchar_t keep=temp[i];temp[i]=0;CreateDirectoryW(temp,0);temp[i]=keep;
    }
    if(!CreateDirectoryW(temp,0)&&!file_exists(temp))return 0;
    return 1;
}
static void error_popup(const wchar_t*msg){MessageBoxW(g_main,msg,L"MarK Setup – Грешка",MB_ICONWARNING);}
static int launch_child(const wchar_t*command,const wchar_t*workdir,int wait,DWORD*outcode){
    wchar_t cmd[8192];scopy(cmd,8192,command);
    STARTUPINFOW si={0};si.cb=sizeof(si);
    PROCESS_INFORMATION pi={0};
    BOOL ok=CreateProcessW(0,cmd,0,0,1,0,0,workdir,&si,&pi);
    if(!ok){return 0;}
    DWORD code=0;
    if(wait){WaitForSingleObject(pi.hProcess,INFINITE);GetExitCodeProcess(pi.hProcess,&code);}
    if(outcode)*outcode=code;
    CloseHandle(pi.hThread);CloseHandle(pi.hProcess);
    return 1;
}
static int run_step(const wchar_t*message,const wchar_t*command,const wchar_t*wd){
    DWORD code=0;
    worker_print(message);
    if(!launch_child(command,wd,1,&code)){
        worker_print(L"ERROR: Failed to launch required official process.");return 0;
    }
    if(code){worker_print(L"ERROR: Required process returned a failure. Review the output above.");return 0;}
    return 1;
}
static int write_embedded_zip(const wchar_t*out){
    HANDLE src=CreateFileW(g_self,GENERIC_READ,FILE_SHARE_READ,0,OPEN_EXISTING,FILE_ATTRIBUTE_NORMAL,0);
    if(src==(HANDLE)-1)return 0;
    long long size=0, pos=0;DWORD got=0,put=0;
    unsigned char footer[16];
    if(!GetFileSizeEx(src,&size)||size<32){CloseHandle(src);return 0;}
    if(!SetFilePointerEx(src,size-16,&pos,FILE_BEGIN)||!ReadFile(src,footer,16,&got,0)||got!=16){CloseHandle(src);return 0;}
    const unsigned char magic[8]={'M','A','R','K','Z','P','0','5'};
    for(int i=0;i<8;i++)if(footer[i]!=magic[i]){CloseHandle(src);return 0;}
    ULONGLONG zip_size=0;
    for(int i=0;i<8;i++)zip_size|=((ULONGLONG)footer[8+i])<<(i*8);
    if(zip_size<10000 || zip_size>(ULONGLONG)(size-16)){CloseHandle(src);return 0;}
    if(!SetFilePointerEx(src,size-16-(long long)zip_size,&pos,FILE_BEGIN)){CloseHandle(src);return 0;}
    HANDLE dst=CreateFileW(out,GENERIC_WRITE,0,0,CREATE_ALWAYS,FILE_ATTRIBUTE_NORMAL,0);
    if(dst==(HANDLE)-1){CloseHandle(src);return 0;}
    ULONGLONG remain=zip_size;int ok=1;
    while(remain){DWORD chunk=(remain>65536)?65536:(DWORD)remain;
        if(!ReadFile(src,g_io,chunk,&got,0)||got!=chunk || !WriteFile(dst,g_io,chunk,&put,0)||put!=chunk){ok=0;break;}
        remain-=chunk;
    }
    CloseHandle(dst);CloseHandle(src);
    return ok;
}
static int verified_python(const wchar_t*path){
    GUID action={0x00AAC56B,0xCD44,0x11D0,{0x8C,0xC2,0x00,0xC0,0x4F,0xC2,0x95,0xEE}};
    WINTRUST_FILE_INFO file={0};file.cbStruct=sizeof(file);file.pcwszFilePath=path;
    WINTRUST_DATA data={0};data.cbStruct=sizeof(data);data.dwUIChoice=2;
    data.fdwRevocationChecks=1;data.dwUnionChoice=1;data.pFile=&file;
    data.dwStateAction=1;data.dwProvFlags=0x00000040;
    LONG result=WinVerifyTrust(0,&action,&data);
    data.dwStateAction=2;WinVerifyTrust(0,&action,&data);
    return result==0;
}
static int installation_worker(const wchar_t*target){
    AllocConsole();SetConsoleTitleW(L"MarK / TD AI Studio PRO – Installer (visible log)");
    worker_print(L"MarK Workspace TEST 1 / x64");
    worker_print(L"Internet required to install Python, Python packages, llama.cpp and AI vision model (>3GB).");
    if(file_exists(target)){
        worker_print(L"STOP: Destination already exists. Choose a new empty installation folder; old data are never overwritten.");return 1;
    }
    unsigned long long free=0,total=0,free2=0;
    if(!GetDiskFreeSpaceExW(target,&free,&total,&free2)){
        /* Query root instead; target may not exist */
        wchar_t drive[4]={target[0],L':',L'\\',0};
        if(!GetDiskFreeSpaceExW(drive,&free,&total,&free2)){
            worker_print(L"STOP: Cannot check free disk space.");return 1;
        }
    }
    if(free<7ULL*1024ULL*1024ULL*1024ULL){worker_print(L"STOP: At least 7 GiB of free space is required.");return 1;}
    if(!make_dirs(target)){worker_print(L"STOP: Could not create selected installation folder.");return 1;}
    wchar_t cache[2048], zip[2048], pythonExe[2048],python_inst[2048];
    joined(cache,2048,target,L"cache");make_dirs(cache);
    joined(zip,2048,cache,L"mark_payload.zip");
    worker_print(L"STEP 1/5 – Extracting audited app package from installer...");
    if(!write_embedded_zip(zip)){worker_print(L"STOP: Embedded ZIP payload missing or incomplete.");return 1;}
    scopy(g_buf,8192,L"\"C:\\Windows\\System32\\tar.exe\" -xf \"");scat(g_buf,8192,zip);scat(g_buf,8192,L"\" -C \"");scat(g_buf,8192,target);scat(g_buf,8192,L"\"");
    if(!run_step(L"Unpacking embedded source-only package using Windows tar.exe ...",g_buf,target))return 1;
    wchar_t nativeApp[2048];joined(nativeApp,2048,target,L"MarK.exe");
    if(!CopyFileW(g_self,nativeApp,1)){worker_print(L"STOP: Could not create installed MarK.exe.");return 1;}
    joined(pythonExe,2048,target,L"runtime\\python\\python.exe");
    joined(python_inst,2048,cache,L"python-3.13.16-amd64.exe");
    worker_print(L"STEP 2/5 – Downloading official Python installer from python.org ...");
    scopy(g_buf,8192,L"\"C:\\Windows\\System32\\curl.exe\" --fail --location --show-error --retry 4 --retry-delay 4 --connect-timeout 40 --output \"");
    scat(g_buf,8192,python_inst);
    scat(g_buf,8192,L"\" \"https://www.python.org/ftp/python/3.13.16/python-3.13.16-amd64.exe\"");
    if(!run_step(L"Please wait – downloading ~27 MB over HTTPS...",g_buf,target))return 1;
    worker_print(L"STEP 3/5 – Checking Python's Windows Authenticode signature ...");
    if(!verified_python(python_inst)){
        worker_print(L"STOP: Python installer signature validation failed (WinVerifyTrust). Nothing was executed.");return 1;
    }
    wchar_t pythonDir[2048];joined(pythonDir,2048,target,L"runtime\\python");make_dirs(pythonDir);
    scopy(g_buf,8192,L"\"");scat(g_buf,8192,python_inst);
    scat(g_buf,8192,L"\" /quiet InstallAllUsers=0 TargetDir=\"");scat(g_buf,8192,pythonDir);
    scat(g_buf,8192,L"\" PrependPath=0 Include_launcher=0 AssociateFiles=0 Shortcuts=0 Include_pip=1 Include_tcltk=1 Include_test=0 Include_doc=0");
    if(!run_step(L"Installing verified Python privately inside selected MarK folder ...",g_buf,target))return 1;
    if(!file_exists(pythonExe)){worker_print(L"STOP: Python runtime was not created in selected folder.");return 1;}
    worker_print(L"STEP 4/5 – Installing TD modules, verified llama.cpp runtime and Qwen local model ...");
    wchar_t task[2048];joined(task,2048,target,L"installer_native\\install_runtime.py");
    scopy(g_buf,8192,L"\"");scat(g_buf,8192,pythonExe);scat(g_buf,8192,L"\" -u \"");scat(g_buf,8192,task);scat(g_buf,8192,L"\"");
    if(!run_step(L"This can take a long time: large AI model downloads. Check data\\install_log.txt.",g_buf,target))return 1;
    worker_print(L"STEP 5/5 – Finished. Open MarK.exe in the selected installation folder.");
    worker_print(L"The old TD installation was not changed. You may close this console.");
    return 0;
}
static int repair_worker(const wchar_t*target){
    AllocConsole();SetConsoleTitleW(L"MarK Workspace - Update");
    wchar_t py[2048],app[2048],script[2048],zip[2048],cache[2048];
    joined(py,2048,target,L"runtime\\python\\python.exe");
    joined(app,2048,target,L"app\\server.py");
    if(!file_exists(py)||!file_exists(app)){worker_print(L"STOP: Select the installed MarK folder.");return 1;}
    joined(cache,2048,target,L"cache\\workspace_update_test1");if(!make_dirs(cache))return 1;
    joined(zip,2048,cache,L"workspace_payload.zip");
    if(!write_embedded_zip(zip)){worker_print(L"STOP: Incomplete installer payload.");return 1;}
    scopy(g_buf,8192,L"\"C:\\Windows\\System32\\tar.exe\" -xf \"");
    scat(g_buf,8192,zip);scat(g_buf,8192,L"\" -C \"");scat(g_buf,8192,cache);
    scat(g_buf,8192,L"\" installer_native/workspace_install.py");
    if(!run_step(L"Checking compatible workspace update ...",g_buf,target))return 1;
    joined(script,2048,cache,L"installer_native\\workspace_install.py");
    scopy(g_buf,8192,L"\"");scat(g_buf,8192,py);scat(g_buf,8192,L"\" -u \"");
    scat(g_buf,8192,script);scat(g_buf,8192,L"\" --target \"");scat(g_buf,8192,target);
    scat(g_buf,8192,L"\" --archive \"");scat(g_buf,8192,zip);scat(g_buf,8192,L"\"");
    if(!run_step(L"Applying compact UI; keeping existing models and records ...",g_buf,target))return 1;
    worker_print(L"SUCCESS: restart MarK using your existing shortcut.");return 0;
}

static int launch_installed(void){
    wchar_t root[2048], exe[2048],file[2048];self_root(root,2048);
    joined(exe,2048,root,L"runtime\\python\\pythonw.exe");
    joined(file,2048,root,L"installer_native\\start_native.py");
    if(!file_exists(exe)||!file_exists(file)){
        MessageBoxW(0,L"MarK runtime is not installed completely. Review the installation log.",L"MarK",MB_ICONWARNING);return 1;
    }
    scopy(g_buf,8192,L"\"");scat(g_buf,8192,exe);scat(g_buf,8192,L"\" \"");scat(g_buf,8192,file);scat(g_buf,8192,L"\"");
    if(!launch_child(g_buf,root,0,0)){
        MessageBoxW(0,L"MarK could not launch Python UI. Check antivirus notifications.",L"MarK",MB_ICONWARNING);return 1;
    }
    return 0;
}
static HWND add_control(LPCWSTR cls,LPCWSTR title,DWORD style,int x,int y,int width,int height,int id){
    return CreateWindowExW(0,cls,title,WS_VISIBLE|WS_CHILD|style,x,y,width,height,g_main,(HANDLE)(unsigned long long)id,g_hinst,0);
}
static void set_default_path(){
    wchar_t root[800];
    if(GetDriveTypeW(L"H:\\")==DRIVE_FIXED){scopy(root,800,L"H:\\");}
    else if(GetEnvironmentVariableW(L"LOCALAPPDATA",root,800)==0){scopy(root,800,L"C:\\");}
    scopy(g_install_path,1024,root);
    if(slen(g_install_path)>3 && g_install_path[slen(g_install_path)-1]!=L'\\')scat(g_install_path,1024,L"\\");
    scat(g_install_path,1024,L"MarK_Workspace_TEST");
}
static void begin_install(){
    wchar_t path[1024];GetWindowTextW(g_edit,path,1024);
    if(slen(path)<5||slen(path)>800||path[1]!=L':'||path[2]!=L'\\'){
        error_popup(L"Избери целосна патека, на пример H:\\MarK_Workspace_TEST");return;
    }
    if(file_exists(path)){error_popup(L"Оваа папка веќе постои. За безбедност, избери нова папка за MarK; старите проекти нема да се менуваат.");return;}
    g_action=1;
    scopy(g_target,1024,path);
    scopy(g_buf,8192,L"\"");scat(g_buf,8192,g_self);scat(g_buf,8192,L"\" --worker \"");scat(g_buf,8192,g_target);scat(g_buf,8192,L"\"");
    STARTUPINFOW si={0};si.cb=sizeof(si);PROCESS_INFORMATION pi={0};
    if(!CreateProcessW(g_self,g_buf,0,0,0,0x00000010UL,0,0,&si,&pi)){
        error_popup(L"Windows не дозволи стартување на native installer worker. Провери Bitdefender; не исклучувај заштита.");return;
    }
    g_worker=pi.hProcess;CloseHandle(pi.hThread);
    EnableWindow(g_install,0);
    EnableWindow(g_repair,0);
    EnableWindow(g_edit,0);
    SetWindowTextW(g_status,L"Инсталацијата работи во видлив прозорец. Следи ги пораките таму.");
    SetTimer(g_main,1,1000,0);
}
static void begin_repair(){
    wchar_t path[1024];GetWindowTextW(g_edit,path,1024);
    if(slen(path)<5||slen(path)>800||path[1]!=L':'||path[2]!=L'\\'){
        error_popup(L"Избери постоечка MarK папка, на пример H:\\MarK_TD_AI_Studio_PRO_Native_v0_4");return;
    }
    wchar_t py[2048], app[2048];
    joined(py,2048,path,L"runtime\\python\\python.exe");
    joined(app,2048,path,L"app\\server.py");
    if(!file_exists(py)||!file_exists(app)){
        error_popup(L"Не е најдена инсталација на MarK. Избери ја папката што содржи app и runtime.");return;
    }
    g_action=2;scopy(g_target,1024,path);
    scopy(g_buf,8192,L"\"");scat(g_buf,8192,g_self);
    scat(g_buf,8192,L"\" --repair-worker \"");
    scat(g_buf,8192,g_target);scat(g_buf,8192,L"\"");
    STARTUPINFOW si={0};si.cb=sizeof(si);PROCESS_INFORMATION pi={0};
    if(!CreateProcessW(g_self,g_buf,0,0,0,0x00000010UL,0,0,&si,&pi)){
        error_popup(L"Windows не дозволи поправка. НЕ исклучувај Bitdefender.");return;
    }
    g_worker=pi.hProcess;CloseHandle(pi.hThread);
    EnableWindow(g_install,0);EnableWindow(g_repair,0);EnableWindow(g_edit,0);
    SetWindowTextW(g_status,L"Надградбата е стартувана. Следи го прозорецот со напредок.");
    SetTimer(g_main,1,1000,0);
}

static LRESULT WINAPI window_proc(HWND hwnd,UINT msg,WPARAM wp,LPARAM lp){
    if(msg==WM_CREATE){
        g_main=hwnd;
        add_control(L"STATIC",L"MarK  |  TD AI Studio PRO",0,24,19,565,35,0);
        add_control(L"STATIC",L"Workspace TEST 1  •  Windows PC",0,24,64,600,22,0);
        add_control(L"STATIC",L"Папка за нова инсталација или за поправка на постоечка:",0,24,115,580,22,0);
        g_edit=add_control(L"EDIT",g_install_path,WS_BORDER|WS_TABSTOP|ES_AUTOHSCROLL,24,145,550,30,201);
        add_control(L"BUTTON",L"Пребарај...",WS_TABSTOP,580,145,105,30,101);
        add_control(L"STATIC",L"Интернет ~3 GB+, 7 GB слободни. Python и AI runtime се инсталираат локално.",0,24,188,661,27,0);
        add_control(L"STATIC",L"Надградба: нов работен изглед. Моделите и личните податоци се зачувуваат.",0,24,220,659,23,0);
        add_control(L"STATIC",L"Прво целосно затвори го MarK пред надградба.",0,24,247,659,23,0);
        g_status=add_control(L"STATIC",L"Подготвено. Ќе се отвори видлив прозорец со текот на инсталацијата.",0,24,296,667,45,0);
        g_install=add_control(L"BUTTON",L"НОВА ИНСТАЛАЦИЈА",WS_TABSTOP|BS_DEFPUSHBUTTON,24,365,208,44,102);
        g_repair=add_control(L"BUTTON",L"НАДГРАДИ ПОСТОЕЧКА",WS_TABSTOP,242,365,207,44,105);
        g_launch=add_control(L"BUTTON",L"СТАРТУВАЈ",WS_TABSTOP,457,365,110,44,104);
        EnableWindow(g_launch,0);
        add_control(L"BUTTON",L"ЗАТВОРИ",WS_TABSTOP,576,365,109,44,103);
        add_control(L"STATIC",L"Тестна верзија: пробај ги модулите и пријави го резултатот.",0,24,426,665,25,0);
        return 0;
    }
    if(msg==WM_COMMAND){
        UINT id=(UINT)(wp&0xffff);
        if(id==101){
            wchar_t selected[1024]={0};
            BROWSEINFOW br={0};br.hwndOwner=hwnd;br.pszDisplayName=selected;br.lpszTitle=L"Избери ПАПКА-РОДИТЕЛ (ќе се креира нова MarK папка)";
            br.ulFlags=0x00000040UL;void* pid=SHBrowseForFolderW(&br);
            if(pid){if(SHGetPathFromIDListW(pid,selected)){
                    wchar_t existing[2048];joined(existing,2048,selected,L"app\\server.py");
                    scopy(g_install_path,1024,selected);
                    if(!file_exists(existing))scat(g_install_path,1024,L"\\MarK_Workspace_TEST");
                    SetWindowTextW(g_edit,g_install_path);
                }CoTaskMemFree(pid);
            }
        } else if(id==102)begin_install();
        else if(id==105)begin_repair();
        else if(id==103)DestroyWindow(hwnd);
        else if(id==104){
            wchar_t exe[2048];joined(exe,2048,g_target,L"MarK.exe");
            scopy(g_buf,8192,L"\"");scat(g_buf,8192,exe);scat(g_buf,8192,L"\"");
            if(!launch_child(g_buf,g_target,0,0))error_popup(L"Не успеа стартувањето. Отвори MarK од постојната икона.");
        }
        return 0;
    }
    if(msg==WM_TIMER){
        if(g_worker){DWORD code=STILL_ACTIVE;
            if(GetExitCodeProcess(g_worker,&code)&&code!=STILL_ACTIVE){
                KillTimer(hwnd,1);g_worker_code=code;
                CloseHandle(g_worker);g_worker=0;
                EnableWindow(g_install,1);EnableWindow(g_repair,1);EnableWindow(g_edit,1);
                if(code==0){
                    if(g_action==2){
                        SetWindowTextW(g_status,L"Работниот изглед е надграден. Стартувај MarK од постојната икона.");
                        MessageBoxW(hwnd,L"Надградбата заврши. Отвори MarK од постојната икона.",L"MarK Repair",MB_ICONINFORMATION);
                    }else{
                        SetWindowTextW(g_status,L"MarK се инсталираше. Кликни СТАРТУВАЈ или отвори MarK.exe во папката.");
                        MessageBoxW(hwnd,L"Инсталацијата заврши. MarK.exe е во избраната папка.",L"MarK Setup",MB_ICONINFORMATION);
                    }
                    EnableWindow(g_launch,1);
                }else{
                    SetWindowTextW(g_status,L"Операцијата запре. Прочитај ги деталите во видливиот прозорец и data логот.");
                    error_popup(L"Операцијата не заврши. Прочитај ги деталите во прозорецот со напредок.");
                }
            }
        }return 0;
    }
    if(msg==WM_DESTROY){PostQuitMessage(0);return 0;}
    return DefWindowProcW(hwnd,msg,wp,lp);
}

void WINAPI WinMainCRTStartup(void){
    g_hinst=GetModuleHandleW(0);
    GetModuleFileNameW(0,g_self,2048);
    wchar_t* command=GetCommandLineW();
    int offset=contains(command,L"--repair-worker \"");
    if(offset>=0){
        offset+=slen(L"--repair-worker \"");wchar_t path[1024];int j=0;
        while(command[offset] && command[offset]!=L'"' && j<1023)path[j++]=command[offset++];path[j]=0;
        if(j==0)ExitProcess(2);
        ExitProcess((UINT)repair_worker(path));
    }
    offset=contains(command,L"--worker \"");
    if(offset>=0){
        offset+=10;wchar_t path[1024];int j=0;
        while(command[offset] && command[offset]!=L'"' && j<1023)path[j++]=command[offset++];path[j]=0;
        if(j==0)ExitProcess(2);
        ExitProcess((UINT)installation_worker(path));
    }
    wchar_t root[2048],p[2048];self_root(root,2048);joined(p,2048,root,L"app\\server.py");
    wchar_t* filename=g_self;for(int i=0;g_self[i];i++)if(g_self[i]==L'\\')filename=g_self+i+1;
    if(seq(filename,L"MarK.exe")&&file_exists(p)){ExitProcess((UINT)launch_installed());}
    set_default_path();
    WNDCLASSW wc={0};wc.lpfnWndProc=window_proc;wc.hInstance=g_hinst;
    wc.lpszClassName=g_cls;wc.hbrBackground=(HBRUSH)(COLOR_WINDOW+1);
    wc.hCursor=LoadCursorW(0,(LPCWSTR)32512);wc.hIcon=LoadIconW(g_hinst,(LPCWSTR)1);
    RegisterClassW(&wc);
    g_main=CreateWindowExW(0,g_cls,L"MarK Workspace — Setup TEST 1",WS_OVERLAPPEDWINDOW,
                         220,120,728,526,0,0,g_hinst,0);
    if(!g_main){MessageBoxW(0,L"Unable to create native Windows setup window.",L"MarK",MB_ICONWARNING);ExitProcess(1);}
    ShowWindow(g_main,SW_SHOW);UpdateWindow(g_main);
    MSG msg={0};while(GetMessageW(&msg,0,0,0)>0){TranslateMessage(&msg);DispatchMessageW(&msg);}
    ExitProcess((UINT)msg.wParam);
}
/* Provide MSVC-compatible intrinsic without requiring the C runtime. */
void *memset(void*dest,int val,unsigned long long count){
    volatile unsigned char*p=(volatile unsigned char*)dest;
    for(unsigned long long i=0;i<count;i++)p[i]=(unsigned char)val;
    return dest;
}
