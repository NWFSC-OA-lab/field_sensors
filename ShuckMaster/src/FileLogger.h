#ifndef FILE_LOGGER_H_
#define FILE_LOGGER_H_

#include <Wire.h>
// #include <DS3231.h>
#include <RTClib.h>
#include <SD.h>

struct FileEntry {
  uint32_t unixTime;
  union {
    float floatVal;
    int32_t intVal;
    uint8_t bytes[4];
  };
};

class FileLogger {
  public:
    FileLogger();
    ~FileLogger();
    bool Init(int chipSelect = 53);
    bool Remove(const DateTime& dt, char *label);
    bool Exists(const DateTime& dt, char *label);
    void LogFloat(const DateTime& dt, char *label, float value);
    void PrintDates(const DateTime& low, const DateTime& high, char *label);
    void PrintFiles();
    int GetPath(char *pathbuf, const DateTime& dt, char *label);
    
    // Opens a stream
    bool Open(const DateTime& low, const DateTime& high, char *label);
    
    // Reads the next available entry
    bool ReadEntry(struct FileEntry *entry);
    
    // Returns true if current date is within the dates specified by open
    bool Available();
    
    // Gets the current label, or null if not open
    char *GetLabel(char *labelStr);
    
    // Closes the current stream
    bool Close();
  private:
    char _strbuf[128];
    char _currLabel[8];
    File _currFile;
    DateTime _lowDate;
    DateTime _highDate;
};

#endif  // FILE_LOGGER_H_