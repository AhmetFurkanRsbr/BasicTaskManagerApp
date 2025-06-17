
#include <windows.h>
#include <tlhelp32.h>
#include <psapi.h>
#include <pdh.h>
#include <iostream>
#include <string>
#include <map>
#include <locale>
#include <fstream>
#include <thread>
#include <chrono>
#include <mutex>
#include <codecvt>

#pragma comment(lib, "pdh.lib")
#pragma comment(lib, "psapi.lib")

using namespace std;

mutex fileMutex; // Dosya eri�im i�in mutex

// A��k uygulamalar� ve i�lemleri saklamak i�in
map<DWORD, wstring> openApplications;  // wstring kullan�yoruz ��nk� Unicode (T�rk�e karakterler i�in) gerekli

// A��k pencereleri bulmak i�in callback fonksiyonu
BOOL CALLBACK enumWindowsCallback(HWND hwnd, LPARAM lParam) {
    DWORD processID;
    GetWindowThreadProcessId(hwnd, &processID);

    wchar_t windowTitle[256];  // Unicode uyumlu (wchar_t)
    GetWindowTextW(hwnd, windowTitle, sizeof(windowTitle) / sizeof(wchar_t));  // Unicode fonksiyonu

    if (IsWindowVisible(hwnd) && wcslen(windowTitle) > 0) {
        openApplications[processID] = wstring(windowTitle);  // Unicode olarak kaydediyoruz
    }
    return TRUE;
}

// ��lem ad�n� almak i�in yard�mc� fonksiyon
wstring getProcessName(DWORD processID) {
    HANDLE hProc = OpenProcess(PROCESS_QUERY_INFORMATION | PROCESS_VM_READ, FALSE, processID);
    if (hProc) {
        wchar_t processName[MAX_PATH] = L"<bilinmiyor>";  // Unicode uyumlu
        if (GetModuleBaseNameW(hProc, NULL, processName, sizeof(processName) / sizeof(wchar_t))) {
            CloseHandle(hProc);
            return wstring(processName);  // Unicode olarak d�nd�r�yoruz
        }
        CloseHandle(hProc);
    }
    return L"<bilinmiyor>";
}

// A��k uygulamalar� ve i�lemleri listeleyen fonksiyon
void listOpenApplications(wofstream& outputFile) {
    openApplications.clear();

    EnumWindows(enumWindowsCallback, 0);

    for (const auto& app : openApplications) {
        DWORD processID = app.first;
        wstring processName = getProcessName(processID);

        // Uygulama ad�n� ve i�lem bilgisini yaz
        outputFile << L"Uygulama Ad�: " << app.second << endl;
        outputFile << L"Process Ad�: " << processName << endl;
        outputFile << L"Process ID: " << processID << endl;
        outputFile << L"-----------------------------------" << endl;
    }
}

// CPU kullan�m�n� hesaplama fonksiyonu
double getCpuUsage() {
    static HQUERY hQuery = NULL;
    static HCOUNTER hCounter;
    static bool initialized = false;

    if (!initialized) {
        if (PdhOpenQuery(NULL, 0, &hQuery) != ERROR_SUCCESS) {
            cerr << "CPU kullan�m sorgusu ba�lat�lamad�!" << endl;
            return -1.0;
        }

        PDH_STATUS status = PdhAddCounter(hQuery, L"\\��lemci Bilgileri(_Total)\\% ��lemci Zaman�", 0, &hCounter);
        if (status != ERROR_SUCCESS) {
            cerr << "CPU kullan�m� �l��m� i�in saya� eklenemedi! Hata kodu: " << status << endl;
            return -1.0;
        }

        Sleep(1000);
        initialized = true;
    }

    if (PdhCollectQueryData(hQuery) == ERROR_SUCCESS) {
        PDH_FMT_COUNTERVALUE counterVal;
        if (PdhGetFormattedCounterValue(hCounter, PDH_FMT_DOUBLE, NULL, &counterVal) == ERROR_SUCCESS) {
            return counterVal.doubleValue;
        }
    }

    cerr << "CPU kullan�m verisi al�namad�!" << endl;
    return -1.0;
}

// Bellek kullan�m�n� hesaplama fonksiyonu
void getMemoryUsage(double& memoryUsagePercent, SIZE_T& memoryUsageMB) {
    MEMORYSTATUSEX memStatus;
    memStatus.dwLength = sizeof(MEMORYSTATUSEX);

    if (GlobalMemoryStatusEx(&memStatus)) {
        memoryUsagePercent = (memStatus.ullTotalPhys - memStatus.ullAvailPhys) * 100.0 / memStatus.ullTotalPhys;
        memoryUsageMB = (memStatus.ullTotalPhys - memStatus.ullAvailPhys) / (1024 * 1024);
    }
    else {
        cerr << "Bellek durumu al�namad�. Hata kodu: " << GetLastError() << endl;
    }
}

void writeDataToFile() {
    // Dosyaya yazma i�lemini kilitleme
    lock_guard<mutex> lock(fileMutex);

    using namespace std::chrono;
    // Veri g�ncelleme s�resinin �l��lmesi
    auto start = high_resolution_clock::now();  // Ba�lang�� zaman�n� al

    // UTF-8 Locale Ayar�
    locale utf8_locale(locale(), new codecvt_utf8<wchar_t>());

    // A��k uygulamalar� kaydetme
    //wofstream appsFile(L"C:\\Users\\HP\\Desktop\\��letim Sistemleri Proje\\Proje\\DonanimdanBilgileriCek\\DonanimdanBilgileriCek\\open_applications.txt", ios::out | ios::trunc);
    wofstream appsFile(L"open_applications.txt", ios::out | ios::trunc);
    if (!appsFile.is_open()) {
        wcerr << L"A��k uygulamalar dosyas� a��lamad�!" << endl;
        return;
    }
    appsFile.imbue(utf8_locale); // UTF-8 i�in locale ayarla
    listOpenApplications(appsFile);
    appsFile.close();

    // CPU ve bellek kullan�m�n� kaydetme
    //wofstream usageFile(L"C:\\Users\\HP\\Desktop\\��letim Sistemleri Proje\\Proje\\DonanimdanBilgileriCek\\DonanimdanBilgileriCek\\system_usage.txt", ios::out | ios::trunc);
    wofstream usageFile(L"system_usage.txt", ios::out | ios::trunc);
    if (!usageFile.is_open()) {
        wcerr << L"Sistem kullan�m� dosyas� a��lamad�!" << endl;
        return;
    }
    usageFile.imbue(utf8_locale); // UTF-8 i�in locale ayarla

    double memoryUsagePercent = 0.0;
    SIZE_T memoryUsageMB = 0;
    getMemoryUsage(memoryUsagePercent, memoryUsageMB);

    double cpuUsagePercent = getCpuUsage();

    usageFile << L"CPU Kullan�m�: " << cpuUsagePercent << L"%" << endl;
    usageFile << L"Bellek Kullan�m�: " << memoryUsagePercent << L"% (" << memoryUsageMB << L" MB)" << endl;
    usageFile.close();


    // Sonu�lar� �l�me
    auto stop = high_resolution_clock::now();  // Biti� zaman�n� al
    auto duration = duration_cast<milliseconds>(stop - start);  // S�reyi milisaniye cinsinden hesapla

    // S�reyi konsola yazd�r
    
    //cout << "Veri g�ncellenme s�resi: " << duration.count() << " ms" << endl;

    //Verinin kontrol edilmesi

    // wcout << L"CPU Kullan�m�: " << cpuUsagePercent << L"%" << endl;
    // wcout << L"Bellek Kullan�m�: " << memoryUsagePercent << L"% (" << memoryUsageMB << L" MB)" << endl;
}


int main() {
    setlocale(LC_ALL, "Turkish");

    while (true) {
        // Dosyaya yazma i�lemi yap
        writeDataToFile();

        // 600 milisaniye bekle
        this_thread::sleep_for(chrono::milliseconds(600));
    }

    return 0;
}

