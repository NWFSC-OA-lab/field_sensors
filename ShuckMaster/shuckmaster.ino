#include <Wire.h>
#include <RTClib.h>
#include <EEPROM.h>
#include "src/FileLogger.h"

#include "src/util/PacketReceiver.h"
#include "src/util/PacketSender.h"
#include "src/Packets.h"

#include "src/Durafet.h"

#include "src/TimedToggleRelay.h"

Durafet df;

TimedToggleRelay pump(23/* TODO pick GPIO pin*/, 5000);

RTC_DS3231 rtc;
FileLogger logger;
char dataLabel[16] = "PH";

HardwareSerial& hm10 = Serial2;

const uint8_t sensorID = 4;

const uint32_t syncPattern = 0x04030201;

const uint32_t eepromMagicAddress = 0x100;
const uint32_t eepromMagicValue = 0xCAFEF00D;

char receiveBuf[256];
PacketReceiver receiver(receiveBuf, syncPattern);

char sendBuf[256];
PacketSender sender(sendBuf, syncPattern);

bool sending = false;

bool sdHealthy = false;
bool rtcHealthy = false;
bool dfHealthy = false;

unsigned long lastByteReadMillis = 0;
unsigned long lastPHStoredMillis = 0;

struct Config cachedConfig;

void setup() {
  Serial.begin(9600);
  Serial2.begin(9600);
  Wire.begin();
  if (logger.Init(53)) {
    Serial.println("SD card initialized successfully");
    sdHealthy = true;
  } else {
    Serial.println("Failed to initialize SD card! uh oh.");
    // while (1) {  }
  }
  
  if (rtc.begin()) {
    Serial.println("RTC initialized successfully");
    rtcHealthy = true;
  } else {
    Serial.println("Couldn't find RTC");
    // while (1) delay();
  }

  lastByteReadMillis = millis();
  lastPHStoredMillis = millis();

  if (df.Begin()) {
    Serial.println("Durafet initialized successfully");
    dfHealthy = true;
  } else {
    Serial.println("Couldn't find Durafet");
  }

  pump.Begin();

  // check eeprom for magic value; if not written, assume first run and initialize persistent config
  uint32_t eepromMagic = 0;
  EEPROM.get(eepromMagicAddress, eepromMagic);
  //if (eepromMagic != eepromMagicValue) {
  if (true) {
    Serial.println("EEPROM magic value not found, initializing configuration");
    cachedConfig;
    cachedConfig.unixtime = rtc.now().unixtime();
    cachedConfig.phPeriod = 10; // 10 seconds
    EEPROM.put(0, cachedConfig);
    EEPROM.put(eepromMagicAddress, eepromMagicValue);
  } else {
    EEPROM.get(0, cachedConfig);
  }

  Serial.println("Config stored:");
  Serial.print("Last set time: ");
  Serial.println(cachedConfig.unixtime);
  Serial.print("PH measurement period: ");
  Serial.println(cachedConfig.phPeriod);
  Serial.print("Standard pH: ");
  Serial.println(cachedConfig.stdPh, 4);
  Serial.print("Standard Temperature: ");
  Serial.println(cachedConfig.stdTemperature, 4);
  Serial.print("Standard Voltage: ");
  Serial.println(cachedConfig.stdVoltage, 4);

  // update calibration with stored values
  df.Calibrate(cachedConfig.stdTemperature, cachedConfig.stdPh, cachedConfig.stdVoltage);
  
  receiver.Begin();
  sending = false;
}

void printDate(const DateTime& date) {
  Serial.print(date.year(), DEC);
  Serial.print("/");
  Serial.print(date.month(), DEC);
  Serial.print("/");
  Serial.print(date.day(), DEC);
  Serial.print(" ");
  Serial.print(date.hour(), DEC);
  Serial.print(':');
  Serial.print(date.minute(), DEC);
  Serial.print(':');
  Serial.print(date.second(), DEC);
}

void loop() {
  df.Tick();
  pump.Tick();

  if (rtcHealthy && sdHealthy && millis() - lastPHStoredMillis > cachedConfig.phPeriod * 1000) {
    float ph = df.GetPh();
    float temp = df.GetTemp();

    Serial.print("Made pH and temperature measurements! pH: ");
    Serial.print(ph, 4);
    Serial.print(",\tTemperature: ");
    Serial.print(temp, 4);
    Serial.println(" C");
    // log ph
    logger.LogFloat(rtc.now(), "pH", ph);

    // log temperature
    logger.LogFloat(rtc.now(), "tp", temp);

    // logger.LogFloat(rtc.now(), "pH", rtc.now().second());
    lastPHStoredMillis = millis();
  }
  
  if (sending) {
    const int N = 10; // number of entries per packet, TODO put this somewhere else
    struct FileEntry entries[N];
    
    // start building packet
    sender.Begin(PACKET_DATA);

    // send number of entries
    // sender.AddByte((byte) (N & 0xFF));
    
    // grab up to N entries from logger (or until entries run out)
    //  - add entry to packet
    Serial.println("-----------");

    byte numEntries = 0;    
    for (int i = 0; i < N && logger.Available(); i++) {
      struct FileEntry entry;
      DateTime dt;
      logger.ReadEntry(&(entries[i]));
      // sender.AddBuf((char *) &entry, sizeof(entry));
      dt = DateTime(entries[i].unixTime);
      printDate(dt);
      Serial.print("\t");
      Serial.println(entries[i].floatVal);
      numEntries++;
    }
    
    // add sensor id
    sender.AddByte(sensorID);
    
    // send number of entries
    sender.AddByte((byte) (numEntries & 0xFF));

    for (int i = 0; i < numEntries; i++) {
      sender.AddBuf((char *) &(entries[i]), sizeof(struct FileEntry));
    }

    // add the label to the end of the packet
    logger.GetLabel(dataLabel);
    sender.AddStr(dataLabel);
    
    // send packet
    sender.Send(hm10);

    // if no more entries, close logger
    if (!logger.Available()) {
      Serial.println("Closing...");
      logger.Close();
      sending = false;
    }
  }
  
  if (hm10.available()) {
    char readByte = hm10.read();
    lastByteReadMillis = millis();    // save time we read a byte at
    if (receiver.AddByte(readByte)) {
      receiver.PrintPacketInfo();
      switch((PacketType)receiver.GetPacketID()) {
        case PACKET_PING: {
          Serial.println("Received Ping packet");
          // send a pong back
          sender.Begin(PACKET_PING);
          sender.Send(hm10);
          break;
        }
        case PACKET_HEALTH: {
          Serial.println("Received Health packet");
          struct HealthPacket healthPacket;
          healthPacket.healthField = 0;
          if (rtc.lostPower()) {
            // lost power since we last checked, unhealthy
            rtcHealthy = false;
          }
          if (rtcHealthy) {
            Serial.println("RTC Healthy!");
            healthPacket.healthField |= 1 << HEALTH_RTC;
          } else {
            Serial.println("RTC Unhealthy!");
          }
          if (sdHealthy) {
            Serial.println("SD Healthy!");
            healthPacket.healthField |= 1 << HEALTH_SD;
          } else {
            Serial.println("SD Unhealthy!");
          }
          sender.Begin(PACKET_HEALTH);
          sender.AddBuf((char *) &healthPacket, sizeof(healthPacket));
          sender.Send(hm10);
          break;
        }
        case PACKET_CONFIG: {
          Serial.println("Received Config packet");
          struct ConfigPacket configPacket = *((struct ConfigPacket *)(receiver.GetPacketData()));
          if (configPacket.configField & (1 << CONFIG_TIME)) {
            Serial.print("Configuring current time: ");
            DateTime newTime(configPacket.config.unixtime);
            printDate(newTime);
            Serial.println();
            
            rtc.adjust(DateTime(configPacket.config.unixtime));
            rtcHealthy = true;  // we just updated the time, should be healthy again until power is lost again
          }
          if (configPacket.configField & (1 << CONFIG_PH_PERIOD)) {
            Serial.print("Configuring pH measurement period: ");
            Serial.println(configPacket.config.phPeriod);
            cachedConfig.phPeriod = configPacket.config.phPeriod;
          }
          if (configPacket.configField & (1 << CONFIG_STD_TEMP)) {
            Serial.print("Calibrating with standard temperature: ");
            Serial.println(configPacket.config.stdTemperature);
            cachedConfig.stdTemperature = configPacket.config.stdTemperature;
          }
          if (configPacket.configField & (1 << CONFIG_STD_PH)) {
            Serial.print("Calibrating with standard pH: ");
            Serial.println(configPacket.config.stdPh);
            cachedConfig.stdPh = configPacket.config.stdPh;
          }
          if (configPacket.configField & (1 << CONFIG_STD_V)) {
            Serial.print("Calibrating with standard voltage: ");
            Serial.println(configPacket.config.stdVoltage);
            cachedConfig.stdVoltage = configPacket.config.stdVoltage;
          }
          EEPROM.put(0, cachedConfig);

          // update durafet calibration values
          df.Calibrate(cachedConfig.stdTemperature, cachedConfig.stdPh, cachedConfig.stdVoltage);
          break;
        }
        case PACKET_DATA: {
          Serial.println("Received Data packet");
          // if logger has available entries (currently sending data), ignore
          if (logger.Available()) {
            Serial.println("Currently sending, ignoring...");
            break;
          }
          struct DataPacket dataPacket = *((struct DataPacket *)(receiver.GetPacketData()));
          DateTime dateLow(dataPacket.unixLow);
          DateTime dateHigh(dataPacket.unixHigh);
          printDate(DateTime(dataPacket.unixLow));
          Serial.println();
          printDate(DateTime(dataPacket.unixHigh));
          Serial.println();

          strcpy(dataLabel, dataPacket.label);
          
          // open logger between dates
          logger.Open(dateLow, dateHigh, dataLabel);
          sending = true;
          break;
        }
        default: {
          Serial.print("Unknown packet id: ");
          Serial.println(receiver.GetPacketID());
          break;
        }
      }
      
      receiver.Begin();
    }
  } else {
    // nothing available, restart receiver state machine if we've stalled for too long (5 seconds)
    if (millis() - lastByteReadMillis > 5000) {
      lastByteReadMillis = millis();
      receiver.Begin();
    }
  }
}
