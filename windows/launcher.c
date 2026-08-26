// Native GUI-subsystem launcher stub for the Windows single-file build.
//
// The final YT-Audio-Downloader.exe is this compiled stub with the app's jar bytes
// appended after it. That works because the Windows PE loader only reads what it needs
// from the headers at the front of the file, and java's zip reader locates the jar's
// central directory by scanning backward from EOF — neither cares about the other's
// data, so one file is simultaneously a valid .exe and a valid .jar.
//
// Being a GUI-subsystem binary (linked with -mwindows), this never allocates a console
// window on its own, unlike the earlier .bat-based launcher (any .bat double-clicked
// from Explorer briefly flashes a cmd.exe console before it can hide anything).
//
// Runs plain "java" (not "javaw") with CREATE_NO_WINDOW: java.exe's stdout/stderr can be
// reliably redirected to a log file this way, so a JVM startup crash is diagnosable from
// %LOCALAPPDATA%\yt-audio-downloader\launcher.log instead of vanishing silently the way
// it would with javaw.
#include <windows.h>
#include <stdio.h>

static BOOL fileExists(const wchar_t *path) {
    DWORD attrib = GetFileAttributesW(path);
    return attrib != INVALID_FILE_ATTRIBUTES && !(attrib & FILE_ATTRIBUTE_DIRECTORY);
}

static void getAppDataDir(wchar_t *out, size_t outSize) {
    wchar_t localAppData[MAX_PATH] = L"";
    GetEnvironmentVariableW(L"LOCALAPPDATA", localAppData, MAX_PATH);
    _snwprintf(out, outSize, L"%s\\yt-audio-downloader", localAppData);
}

static void getPortableJrePath(wchar_t *out, size_t outSize) {
    wchar_t appDir[MAX_PATH];
    getAppDataDir(appDir, MAX_PATH);
    _snwprintf(out, outSize, L"%s\\jre\\bin\\java.exe", appDir);
}

static void getLogPath(wchar_t *out, size_t outSize) {
    wchar_t appDir[MAX_PATH];
    getAppDataDir(appDir, MAX_PATH);
    CreateDirectoryW(appDir, NULL);
    _snwprintf(out, outSize, L"%s\\launcher.log", appDir);
}

// Runs a command line with its console suppressed (CREATE_NO_WINDOW) but stdout/stderr
// redirected to the log file, so startup crashes are diagnosable without ever showing a
// console window to the user.
static BOOL runLogged(wchar_t *cmdLine, BOOL wait) {
    wchar_t logPath[MAX_PATH];
    getLogPath(logPath, MAX_PATH);

    SECURITY_ATTRIBUTES sa;
    ZeroMemory(&sa, sizeof(sa));
    sa.nLength = sizeof(sa);
    sa.bInheritHandle = TRUE;
    HANDLE hLog = CreateFileW(logPath, FILE_APPEND_DATA, FILE_SHARE_READ | FILE_SHARE_WRITE,
                              &sa, CREATE_ALWAYS, FILE_ATTRIBUTE_NORMAL, NULL);

    STARTUPINFOW si;
    ZeroMemory(&si, sizeof(si));
    si.cb = sizeof(si);
    // CREATE_NO_WINDOW below is what actually suppresses the console — deliberately not
    // also setting STARTF_USESHOWWINDOW/SW_HIDE here, since some JDK builds query the
    // inherited show-state during AWT/window-toolkit init and a "hidden" hint can make
    // that init path behave unexpectedly.
    if (hLog != INVALID_HANDLE_VALUE) {
        si.dwFlags |= STARTF_USESTDHANDLES;
        si.hStdOutput = hLog;
        si.hStdError = hLog;
    }

    PROCESS_INFORMATION pi;
    ZeroMemory(&pi, sizeof(pi));
    BOOL ok = CreateProcessW(NULL, cmdLine, NULL, NULL, TRUE, CREATE_NO_WINDOW, NULL, NULL, &si, &pi);
    if (hLog != INVALID_HANDLE_VALUE) {
        CloseHandle(hLog);
    }
    if (!ok) {
        return FALSE;
    }
    if (wait) {
        WaitForSingleObject(pi.hProcess, INFINITE);
    }
    CloseHandle(pi.hProcess);
    CloseHandle(pi.hThread);
    return TRUE;
}

// Same Adoptium-download-and-extract logic as the previous bat-header.bat, just run
// from here instead. Downloads a portable Temurin 21 JRE into
// %LOCALAPPDATA%\yt-audio-downloader\jre on machines with no system Java.
static const wchar_t *BOOTSTRAP_PS_BASE64 =
    L"JABFAHIAcgBvAHIAQQBjAHQAaQBvAG4AUAByAGUAZgBlAHIAZQBuAGMAZQAgAD0AIAAnAFMAdABvAHAAJwAKACQAbABvAGMAYQBsAEoAcgBlACAAPQAgAEoAbwBpAG4ALQBQAGEAdABoACAAJABlAG4AdgA6AEwATwBDAEEATABBAFAAUABEAEEAVABBACAAJwB5AHQALQBhAHUAZABpAG8ALQBkAG8AdwBuAGwAbwBhAGQAZQByAFwAagByAGUAJwAKACQAegBpAHAAIAA9ACAASgBvAGkAbgAtAFAAYQB0AGgAIAAkAGUAbgB2ADoAVABFAE0AUAAgACcAeQB0AGEAdQBkAGkAbwAtAGoAcgBlAC4AegBpAHAAJwAKACQAdQByAGwAIAA9ACAAJwBoAHQAdABwAHMAOgAvAC8AYQBwAGkALgBhAGQAbwBwAHQAaQB1AG0ALgBuAGUAdAAvAHYAMwAvAGIAaQBuAGEAcgB5AC8AbABhAHQAZQBzAHQALwAyADEALwBnAGEALwB3AGkAbgBkAG8AdwBzAC8AeAA2ADQALwBqAHIAZQAvAGgAbwB0AHMAcABvAHQALwBuAG8AcgBtAGEAbAAvAGUAYwBsAGkAcABzAGUAPwBwAHIAbwBqAGUAYwB0AD0AagBkAGsAJwAKAEkAbgB2AG8AawBlAC0AVwBlAGIAUgBlAHEAdQBlAHMAdAAgAC0AVQByAGkAIAAkAHUAcgBsACAALQBPAHUAdABGAGkAbABlACAAJAB6AGkAcAAgAC0AVQBzAGUAQgBhAHMAaQBjAFAAYQByAHMAaQBuAGcACgBOAGUAdwAtAEkAdABlAG0AIAAtAEkAdABlAG0AVAB5AHAAZQAgAEQAaQByAGUAYwB0AG8AcgB5ACAALQBGAG8AcgBjAGUAIAAtAFAAYQB0AGgAIAAkAGwAbwBjAGEAbABKAHIAZQAgAHwAIABPAHUAdAAtAE4AdQBsAGwACgBFAHgAcABhAG4AZAAtAEEAcgBjAGgAaQB2AGUAIAAtAFAAYQB0AGgAIAAkAHoAaQBwACAALQBEAGUAcwB0AGkAbgBhAHQAaQBvAG4AUABhAHQAaAAgACQAbABvAGMAYQBsAEoAcgBlACAALQBGAG8AcgBjAGUACgAkAGkAbgBuAGUAcgAgAD0AIABHAGUAdAAtAEMAaABpAGwAZABJAHQAZQBtACAALQBQAGEAdABoACAAJABsAG8AYwBhAGwASgByAGUAIAB8ACAAVwBoAGUAcgBlAC0ATwBiAGoAZQBjAHQAIAB7ACAAJABfAC4AUABTAEkAcwBDAG8AbgB0AGEAaQBuAGUAcgAgAH0AIAB8ACAAUwBlAGwAZQBjAHQALQBPAGIAagBlAGMAdAAgAC0ARgBpAHIAcwB0ACAAMQAKAGkAZgAgACgAJABpAG4AbgBlAHIAKQAgAHsACgAgACAAIAAgAEcAZQB0AC0AQwBoAGkAbABkAEkAdABlAG0AIAAtAFAAYQB0AGgAIAAkAGkAbgBuAGUAcgAuAEYAdQBsAGwATgBhAG0AZQAgAHwAIABNAG8AdgBlAC0ASQB0AGUAbQAgAC0ARABlAHMAdABpAG4AYQB0AGkAbwBuACAAJABsAG8AYwBhAGwASgByAGUAIAAtAEYAbwByAGMAZQAKACAAIAAgACAAUgBlAG0AbwB2AGUALQBJAHQAZQBtACAALQBQAGEAdABoACAAJABpAG4AbgBlAHIALgBGAHUAbABsAE4AYQBtAGUAIAAtAFIAZQBjAHUAcgBzAGUAIAAtAEYAbwByAGMAZQAKAH0ACgBSAGUAbQBvAHYAZQAtAEkAdABlAG0AIAAtAFAAYQB0AGgAIAAkAHoAaQBwACAALQBGAG8AcgBjAGUACgA=";

int WINAPI wWinMain(HINSTANCE hInstance, HINSTANCE hPrevInstance, LPWSTR lpCmdLine, int nCmdShow) {
    wchar_t selfPath[MAX_PATH];
    GetModuleFileNameW(NULL, selfPath, MAX_PATH);

    wchar_t cmd[MAX_PATH * 2 + 64];

    // 1) System Java on PATH.
    _snwprintf(cmd, sizeof(cmd) / sizeof(wchar_t),
               L"java -Dprism.lcdtext=false -jar \"%s\"", selfPath);
    if (runLogged(cmd, FALSE)) {
        return 0;
    }

    // 2) Portable JRE already downloaded on a previous run.
    wchar_t jrePath[MAX_PATH];
    getPortableJrePath(jrePath, MAX_PATH);
    if (fileExists(jrePath)) {
        _snwprintf(cmd, sizeof(cmd) / sizeof(wchar_t),
                   L"\"%s\" -Dprism.lcdtext=false -jar \"%s\"", jrePath, selfPath);
        runLogged(cmd, FALSE);
        return 0;
    }

    // 3) No Java anywhere: download a portable one (one-time, ~45MB), then launch it.
    wchar_t psCmd[8192];
    _snwprintf(psCmd, sizeof(psCmd) / sizeof(wchar_t),
               L"powershell -NoProfile -ExecutionPolicy Bypass -EncodedCommand %s",
               BOOTSTRAP_PS_BASE64);
    runLogged(psCmd, TRUE);

    if (fileExists(jrePath)) {
        _snwprintf(cmd, sizeof(cmd) / sizeof(wchar_t),
                   L"\"%s\" -Dprism.lcdtext=false -jar \"%s\"", jrePath, selfPath);
        runLogged(cmd, FALSE);
    } else {
        MessageBoxW(NULL,
                     L"Falha ao preparar o Java automaticamente. Instale manualmente em: https://adoptium.net",
                     L"YT Audio Downloader", MB_ICONERROR | MB_OK);
    }
    return 0;
}
