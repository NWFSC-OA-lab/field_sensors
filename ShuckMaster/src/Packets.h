#ifndef PACKETS_H_
#define PACKETS_H_

#include <Arduino.h>

enum PacketType {
  PACKET_PING = 0x00,
  PACKET_HEALTH = 0x01,
  PACKET_CONFIG = 0x02,
  PACKET_DATA = 0x08
};

enum HealthType {
  HEALTH_RTC = 0,
  HEALTH_SD = 1,
  HEALTH_PH = 2,
  HEALTH_TEMP = 3,
  HEALTH_RESERVED_1 = 4,
  HEALTH_RESERVED_2 = 5,
  HEALTH_RESERVED_3 = 6,
  HEALTH_RESERVED_4 = 7
};

struct HealthPacket {
  uint8_t healthField;
};

enum ConfigType {
  CONFIG_TIME = 0,
  CONFIG_PH_PERIOD = 1,
  
  // for future use
  CONFIG_STD_TEMP = 2,
  CONFIG_STD_PH_LOW = 3,
  CONFIG_STD_PH_HIGH = 4,
  CONFIG_STD_LOWCON = 5,
  CONFIG_STD_HIGHCON = 6,
  //CONFIG_RESERVED_4 = 5,
  //CONFIG_RESERVED_5 = 6,
  CONFIG_RESERVED_6 = 7
};


struct Config {
  uint32_t unixtime;
  uint32_t phPeriod;
  float stdTemperature;
  float stdPhLow;
  float stdPhHigh;
  float stdLowCon;
  float stdHighCon;
  //float stdOceanTemp; //temp until we get a temperature sensor. Replace back with reserved_6
  //uint32_t reserved_4;
  //uint32_t reserved_5;
  uint32_t reserved_6;
  
};

// ATMega2560 is 8-bit, no padding necessary

struct ConfigPacket {
  uint8_t configField;
  struct Config config;
};

struct DataPacket {
  uint32_t unixLow;
  uint32_t unixHigh;
  char label[8];
  };

#endif  // PACKETS_H_
