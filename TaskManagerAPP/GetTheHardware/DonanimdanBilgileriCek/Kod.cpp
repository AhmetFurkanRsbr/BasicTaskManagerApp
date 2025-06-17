
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

mutex fileMutex; // Dosya eriþim için mutex

// Açýk uygulamalarý ve iþlemleri saklamak için
map<DWORD, wstring> openApplications;  // wstring kullanýyoruz çünkü Unicode (Türkçe karakterler için) gerekli

// Açýk pencereleri bulmak için callback fonksiyonu
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

// Ýþlem adýný almak için yardýmcý fonksiyon
wstring getProcessName(DWORD processID) {
    HANDLE hProc = OpenProcess(PROCESS_QUERY_INFORMATION | PROCESS_VM_READ, FALSE, processID);
    if (hProc) {
        wchar_t processName[MAX_PATH] = L"<bilinmiyor>";  // Unicode uyumlu
        if (GetModuleBaseNameW(hProc, NULL, processName, sizeof(processName) / sizeof(wchar_t))) {
            CloseHandle(hProc);
            return wstring(processName);  // Unicode olarak döndürüyoruz
        }
        CloseHandle(hProc);
    }
    return L"<bilinmiyor>";
}

// Açýk uygulamalarý ve iþlemleri listeleyen fonksiyon
void listOpenApplications(wofstream& outputFile) {
    openApplications.clear();

    EnumWindows(enumWindowsCallback, 0);

    for (const auto& app : openApplications) {
        DWORD processID = app.first;
        wstring processName = getProcessName(processID);

        // Uygulama adýný ve iþlem bilgisini yaz
        outputFile << L"Uygulama Adý: " << app.second << endl;
        outputFile << L"Process Adý: " << processName << endl;
        outputFile << L"Process ID: " << processID << endl;
        outputFile << L"-----------------------------------" << endl;
    }
}

// CPU kullanýmýný hesaplama fonksiyonu
double getCpuUsage() {
    static HQUERY hQuery = NULL;
    static HCOUNTER hCounter;
    static bool initialized = false;

    if (!initialized) {
        if (PdhOpenQuery(NULL, 0, &hQuery) != ERROR_SUCCESS) {
            cerr << "CPU kullaným sorgusu baþlatýlamadý!" << endl;
            return -1.0;
        }

        PDH_STATUS status = PdhAddCounter(hQuery, L"\\Ýþlemci Bilgileri(_Total)\\% Ýþlemci Zamaný", 0, &hCounter);
        if (status != ERROR_SUCCESS) {
            cerr << "CPU kullanýmý ölçümü için sayaç eklenemedi! Hata kodu: " << status << endl;
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

    cerr << "CPU kullaným verisi alýnamadý!" << endl;
    return -1.0;
}

// Bellek kullanýmýný hesaplama fonksiyonu
void getMemoryUsage(double& memoryUsagePercent, SIZE_T& memoryUsageMB) {
    MEMORYSTATUSEX memStatus;
    memStatus.dwLength = sizeof(MEMORYSTATUSEX);

    if (GlobalMemoryStatusEx(&memStatus)) {
        memoryUsagePercent = (memStatus.ullTotalPhys - memStatus.ullAvailPhys) * 100.0 / memStatus.ullTotalPhys;
        memoryUsageMB = (memStatus.ullTotalPhys - memStatus.ullAvailPhys) / (1024 * 1024);
    }
    else {
        cerr << "Bellek durumu alýnamadý. Hata kodu: " << GetLastError() << endl;
    }
}

void writeDataToFile() {
    // Dosyaya yazma iþlemini kilitleme
    lock_guard<mutex> lock(fileMutex);

    using namespace std::chrono;
    // Veri güncelleme süresinin ölçülmesi
    auto start = high_resolution_clock::now();  // Baþlangýç zamanýný al

    // UTF-8 Locale Ayarý
    locale utf8_locale(locale(), new codecvt_utf8<wchar_t>());

    // Açýk uygulamalarý kaydetme
    //wofstream appsFile(L"C:\\Users\\HP\\Desktop\\Ýþletim Sistemleri Proje\\Proje\\DonanimdanBilgileriCek\\DonanimdanBilgileriCek\\open_applications.txt", ios::out | ios::trunc);
    wofstream appsFile(L"open_applications.txt", ios::out | ios::trunc);
    if (!appsFile.is_open()) {
        wcerr << L"Açýk uygulamalar dosyasý açýlamadý!" << endl;
        return;
    }
    appsFile.imbue(utf8_locale); // UTF-8 için locale ayarla
    listOpenApplications(appsFile);
    appsFile.close();

    // CPU ve bellek kullanýmýný kaydetme
    //wofstream usageFile(L"C:\\Users\\HP\\Desktop\\Ýþletim Sistemleri Proje\\Proje\\DonanimdanBilgileriCek\\DonanimdanBilgileriCek\\system_usage.txt", ios::out | ios::trunc);
    wofstream usageFile(L"system_usage.txt", ios::out | ios::trunc);
    if (!usageFile.is_open()) {
        wcerr << L"Sistem kullanýmý dosyasý açýlamadý!" << endl;
        return;
    }
    usageFile.imbue(utf8_locale); // UTF-8 için locale ayarla

    double memoryUsagePercent = 0.0;
    SIZE_T memoryUsageMB = 0;
    getMemoryUsage(memoryUsagePercent, memoryUsageMB);

    double cpuUsagePercent = getCpuUsage();

    usageFile << L"CPU Kullanýmý: " << cpuUsagePercent << L"%" << endl;
    usageFile << L"Bellek Kullanýmý: " << memoryUsagePercent << L"% (" << memoryUsageMB << L" MB)" << endl;
    usageFile.close();


    // Sonuçlarý ölçme
    auto stop = high_resolution_clock::now();  // Bitiþ zamanýný al
    auto duration = duration_cast<milliseconds>(stop - start);  // Süreyi milisaniye cinsinden hesapla

    // Süreyi konsola yazdýr
    
    //cout << "Veri güncellenme süresi: " << duration.count() << " ms" << endl;

    //Verinin kontrol edilmesi

    // wcout << L"CPU Kullanýmý: " << cpuUsagePercent << L"%" << endl;
    // wcout << L"Bellek Kullanýmý: " << memoryUsagePercent << L"% (" << memoryUsageMB << L" MB)" << endl;
}


int main() {
    setlocale(LC_ALL, "Turkish");

    while (true) {
        // Dosyaya yazma iþlemi yap
        writeDataToFile();

        // 600 milisaniye bekle
        this_thread::sleep_for(chrono::milliseconds(600));
    }

    return 0;
}

