#include <Arduino.h>
#include <SPI.h>

#include "FileLogger.h"

TimeSpan ONE_DAY(1, 0, 0, 0);

void printFile(File file, int indent = 0) {
  // Prints out directory of where the file is.
  if (file.isDirectory()) {
    Serial.print(F(" DIR  "));
    for (int i = 0; i < indent; i++) {
      Serial.print("  ");
    }
    Serial.print(file.name());
    
    // directory
    File entry;
    while (entry = file.openNextFile()) {
      // there's a file we haven't opened in this directory
      Serial.println();
      printFile(entry, indent + 1);
    }
  } else {
    Serial.print(F("FILE  "));
    for (int i = 0; i < indent; i++) {
      Serial.print("  ");
    }
    Serial.print(file.name());
  }
  file.close();
}

FileLogger::FileLogger() : _currFile(File()){
  
}

FileLogger::~FileLogger() {
  SD.end();   // close SD card
}

bool FileLogger::Init(int chipSelect) {
  return SD.begin(chipSelect);
}

bool FileLogger::Remove(const DateTime& dt, char *label) {
  GetPath(_strbuf, dt, label);
  // Serial.println(_strbuf);
  return SD.remove(_strbuf);
}

bool FileLogger::Exists(const DateTime& dt, char *label) {
  GetPath(_strbuf, dt, label);
  // Serial.println(_strbuf);
  return SD.exists(_strbuf);
}

void FileLogger::LogFloat(const DateTime& dt, char *label, float value) {
  GetPath(_strbuf, dt, label);
  
  /*
  if (SD.remove(_strbuf)) {
    Serial.println(_strbuf);
  }
  */
  
  struct FileEntry entry;
  entry.unixTime = dt.unixtime();
  entry.floatVal = value;
  
  File file = SD.open(_strbuf, FILE_WRITE);
  // file.seek(EOF);
  int count = file.write((char *) &entry, sizeof(entry));
  if (count != sizeof(entry)) {
    Serial.println(count);
  }
  
  file.close();
}

void FileLogger::PrintDates(const DateTime& low, const DateTime& high, char *label) {
  DateTime lowDate(low.year(), low.month(), low.day());
  DateTime highDate(high.year(), high.month(), high.day());
  TimeSpan oneDay(1, 0, 0, 0);
  while (lowDate <= highDate) {
    /*
    Serial.print(lowDate.year(), DEC);
    Serial.print("/");
    Serial.print(lowDate.month(), DEC);
    Serial.print("/");
    Serial.print(lowDate.day(), DEC);
    Serial.println();
    */
    GetPath(_strbuf, lowDate, label);
    if (SD.exists(_strbuf)) {
      Serial.println(_strbuf);
      // Serial.println(" exists");
      File dayFile = SD.open(_strbuf);  // open file for reading
      Serial.println(dayFile.size());
      struct FileEntry entry;
      DateTime dt;
      while (dayFile.available() >= sizeof(struct FileEntry)) {
        dayFile.read(&entry, sizeof(struct FileEntry));
        dt = DateTime(entry.unixTime);
        
        /*
        Serial.print(dt.year(), DEC);
        Serial.print("/");
        Serial.print(dt.month(), DEC);
        Serial.print("/");
        Serial.print(dt.day(), DEC);
        Serial.print(" ");
        Serial.print(dt.hour(), DEC);
        Serial.print("\t");
        
        Serial.println(entry.floatVal);
        */
        Serial.print(entry.unixTime, HEX);
        Serial.println(entry.floatVal, HEX);
      }
      dayFile.close();
    }
    
    lowDate = lowDate + oneDay;
  }
}

// Opens a stream
bool FileLogger::Open(const DateTime& low, const DateTime& high, char *label) {
  // find the earliest file with the given label in [low, high]
  //  - if no file found, return false
  // open earliest file, store
  
  _lowDate = low;
  _highDate = high;
  strcpy(_currLabel, label);
  
  while (_lowDate <= _highDate) {
    GetPath(_strbuf, _lowDate, _currLabel);
    if (SD.exists(_strbuf)) {
      Serial.println(_strbuf);
      _currFile = SD.open(_strbuf);  // open file for reading
      if (_currFile.available() >= sizeof(struct FileEntry)) {
        // found first earliest file with at least one entry, leave it open
        return true;
      }
      
      // file found doesn't have an entry, continue to the next
      _currFile.close();
    }
    _lowDate = _lowDate + ONE_DAY;
  }
  
  // didn't find any files within the time period
  return false;
}

// Reads the next available entry
// pre: stream opened, available() is true
bool FileLogger::ReadEntry(struct FileEntry *entry) {
  // read one entry from stored file
  // if after reading there are no more available bytes, close this file
  //  - if there's a next earliest date, open it
  int size = sizeof(struct FileEntry);
  if (_currFile.read(entry, size) != size) {
    // failed read
    _currFile.close();
    return false;
  }
  
  if (_currFile.available() < size) {
    // not enough bytes left to fit an entry, we're done with this file
    _lowDate = _lowDate + ONE_DAY;
    
    while (_lowDate <= _highDate) {
      GetPath(_strbuf, _lowDate, _currLabel);
      if (SD.exists(_strbuf)) {
        Serial.println(_strbuf);
        _currFile = SD.open(_strbuf);  // open file for reading
        if (_currFile.available() >= sizeof(struct FileEntry)) {
          // found first earliest file with at least one entry, leave it open
          break;
        }
        
        // file found doesn't have an entry, continue to the next
        _currFile.close();
      }
      _lowDate = _lowDate + ONE_DAY;
    }
  }
  
  return true;
}

// Returns true if current date is within the 
bool FileLogger::Available() {
  return _currFile && _currFile.available() >= sizeof(struct FileEntry);
}

// Gets the current label, or nullptr if not open
char *FileLogger::GetLabel(char *labelStr) {
  if (Available()) {
    return strcpy(labelStr, _currLabel);
  } else {
    return nullptr;
  }
}

// Closes the current stream
bool FileLogger::Close() {
  // if _currFile is open, close it
  if (_currFile) {
    _currFile.close();
  }
}

void FileLogger::PrintFiles() {
  File root = SD.open("/");
  printFile(root);
  Serial.println();
}

int FileLogger::GetPath(char *pathbuf, const DateTime& dt, char *label) {
  /*
  Serial.print(dt.year(), DEC);
  Serial.print("/");
  Serial.print(dt.month(), DEC);
  Serial.print("/");
  Serial.print(dt.day(), DEC);
  Serial.print(" ");
  Serial.print(dt.hour(), DEC);
  Serial.println();
  */
  
  return snprintf(pathbuf, 16, "%02d%02d%02d%s.SHK", dt.year() - 2000, dt.month(), dt.day(), label);
}
