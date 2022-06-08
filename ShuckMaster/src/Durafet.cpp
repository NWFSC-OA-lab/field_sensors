#include <Arduino.h>
#include "Durafet.h"

float average(float numbers[], int len) {
  float sum = 0;
  for (int x = 0; x < len; x++) {
    sum += numbers[x];
  }
  return sum /(float)len;
}

Durafet::Durafet(): _ads(Adafruit_ADS1X15()), _itr(0), _averages(100), _E0(0), _T_S(0), _lastTick(0) {
  
}

bool Durafet::Begin() {
  _lastTick = millis();
  _ads.setGain(GAIN_TWO);
  return _ads.begin();
}

void Durafet::Tick() {
  if (millis() - _lastTick > 1000) {
    int16_t results1, results2;  
    float multiplier = 0.0625;
    results1 = _ads.readADC_Differential_0_1();
    results2 = _ads.readADC_Differential_2_3();
    
    int high = analogRead(A0);
    int low = analogRead(A1);
    float voltage = (high - low) * (5.0 / 1023.0);
    float current = (results2 * multiplier / 1000.0) / 9990.0;
    float v_thermistor = voltage - (results2 * multiplier / 1000.0);
    
    float res = v_thermistor / current;
    
    float T_K = (1 / (SH_A + SH_B * log(res) + SH_C * pow(log(res), 3))) + 0.2368;
    
    // Serial.println("----------------");
    
    // float avg;
    if (_itr < _averages) {
      _values2[_itr] = T_K;
      _lastTemp = average(_values2, _itr+1);
    } else {
      for (int j = 0; j < _averages-1; ++j) {
        _values2[j] = _values2[j+1];
      }
      _values2[_averages-1] = T_K;
      _lastTemp = average(_values2, _averages);
    }
    // Serial.println(avg);
    
    float S_T = R * T_K / F * log(10);
    float E0_T = _E0 + E_dT * (T_K - _T_S);

    _lastPh = ((results1 * multiplier / 1000.0) - E0_T) / S_T;
    
    if (_itr < _averages) {
      _values1[_itr] = _lastPh;
      _itr = _itr + 1;
    } else {
      for (int j = 0; j < _averages-1; ++j) {
        _values1[j] = _values1[j+1];
      }
      _values1[_averages-1] = _lastPh;
    }
    // Serial.println(pH);
    
    _lastTick = millis();
  }
}

// sets _E_0, _T_S
void Durafet::Calibrate(float standardTemp, float standardPh, float standardVoltage) {
  float ln10 = 2.30258509299;
  _T_S = ZERO_C_K + standardTemp;
  _E0 = standardVoltage - standardPh * _T_S * R * ln10 / F;
}

float Durafet::GetPh() {
  return _lastPh;
}
float Durafet::GetTemp() {
  return _lastTemp - ZERO_C_K;
}