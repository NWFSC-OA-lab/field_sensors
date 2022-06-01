#ifndef DURAFET_H_
#define DURAFET_H_

#include <Adafruit_ADS1X15.h>

class Durafet {
 public:
  Durafet();
  bool Begin();
  
  void Tick();
  
  void Calibrate(float standardTemp, float standardPh, float standardVoltage);
  
  float GetPh();
  float GetTemp();
  
 private:
  const float E_dT = -0.001;
  const float R = 8.31451;
  const float F = 96487;
  const float ZERO_C_K = 273.15;
  
  const float SH_A = 0.00106329736674527;
  const float SH_B = 0.000251377462346306;
  const float SH_C = 0.0000000255455247726963;
 
  Adafruit_ADS1X15 _ads;
  int _itr;
  int _averages;
  float _values1[100];
  float _values2[100];
  float _E0;
  float _T_S;
  unsigned long _lastTick;
  float _lastTemp;
  float _lastPh;
};

#endif