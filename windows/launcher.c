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
#include <windows.h>
#include <stdio.h>

static BOOL fileExists(const wchar_t *path) {
    DWORD attrib = GetFileAttributesW(path);
    return attrib != INVALID_FILE_ATTRIBUTES && !(attrib & FILE_ATTRIBUTE_DIRECTORY);
}

static BOOL runProcess(wchar_t *cmdLine, BOOL wait) {
    STARTUPINFOW si;
    PROCESS_INFORMATION pi;
    ZeroMemory(&si, sizeof(si));
    si.cb = sizeof(si);
    si.dwFlags = STARTF_USESHOWWINDOW;
    si.wShowWindow = SW_HIDE;
    ZeroMemory(&pi, sizeof(pi));

    BOOL ok = CreateProcessW(NULL, cmdLine, NULL, NULL, FALSE, CREATE_NO_WINDOW, NULL, NULL, &si, &pi);
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

static void getPortableJrePath(wchar_t *out, size_t outSize) {
    wchar_t localAppData[MAX_PATH] = L"";
    GetEnvironmentVariableW(L"LOCALAPPDATA", localAppData, MAX_PATH);
    _snwprintf(out, outSize, L"%s\\yt-audio-downloader\\jre\\bin\\javaw.exe", localAppData);
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
               L"javaw -Dprism.lcdtext=false -jar \"%s\"", selfPath);
    if (runProcess(cmd, FALSE)) {
        return 0;
    }

    // 2) Portable JRE already downloaded on a previous run.
    wchar_t jrePath[MAX_PATH];
    getPortableJrePath(jrePath, MAX_PATH);
    if (fileExists(jrePath)) {
        _snwprintf(cmd, sizeof(cmd) / sizeof(wchar_t),
                   L"\"%s\" -Dprism.lcdtext=false -jar \"%s\"", jrePath, selfPath);
        runProcess(cmd, FALSE);
        return 0;
    }

    // 3) No Java anywhere: download a portable one (one-time, ~45MB), then launch it.
    wchar_t psCmd[8192];
    _snwprintf(psCmd, sizeof(psCmd) / sizeof(wchar_t),
               L"powershell -NoProfile -ExecutionPolicy Bypass -EncodedCommand %s",
               BOOTSTRAP_PS_BASE64);
    runProcess(psCmd, TRUE);

    if (fileExists(jrePath)) {
        _snwprintf(cmd, sizeof(cmd) / sizeof(wchar_t),
                   L"\"%s\" -Dprism.lcdtext=false -jar \"%s\"", jrePath, selfPath);
        runProcess(cmd, FALSE);
    } else {
        MessageBoxW(NULL,
                     L"Falha ao preparar o Java automaticamente. Instale manualmente em: https://adoptium.net",
                     L"YT Audio Downloader", MB_ICONERROR | MB_OK);
    }
    return 0;
}
