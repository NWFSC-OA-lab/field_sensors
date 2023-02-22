#include <Arduino.h>
#include "Durafet.h"

float analogReadSteps = 1023.0; //how many bits the analog read returns
float analogReadFullScale = 5.0; //how many volts the full scale analog read measurement corresponds to
float thermistorSeriesResistance = 10000.0; //in ohms

//these constants are calculated for GAIN_TWO, need to recalculate for other gains
//https://github.com/adafruit/Adafruit_ADS1X15/blob/master/examples/differential/differential.ino
//see this example for how to use different gain ranges
float ADCreadSteps = 4095.0; //how many bits ADC read returns
float bitsPerVolt = 1000.0; //1 bit is 1mV for GAIN_TWO
float ADCreadOffset = 2.048; //what voltage a full scale reading corresponds to

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
	
	float results1_volts;
	results1_volts = _ads.computeVolts(results1);
	
	//print ph probe voltage for calibration
	//Serial.print("PH probe voltage: ");
	//Serial.println(results1_volts);
	
	float results2_volts;
	results2_volts = _ads.computeVolts(results2);
	
	//print thermistor voltage for calibration
	//Serial.print("Thermistor Voltage: ");
	//Serial.println(results2_volts);
    
	//circuit for thermistor should be thermistor in series with a resistor across a voltage, in this case a 10k ohm resistor and across 3.3V. 
    float thermistor_posvoltage = (analogRead(A0) / analogReadSteps) * analogReadFullScale;
    float thermistor_negvoltage = (analogRead(A1) / analogReadSteps) * analogReadFullScale;
	
	float thermistor_differentialvoltage = thermistor_posvoltage - thermistor_negvoltage;

    //resistance of thermistor
	float thermistorCurrent = results2_volts / thermistorSeriesResistance;
	
    float res = thermistor_differentialvoltage / thermistorCurrent - thermistorSeriesResistance;
    
    float T_K = (1 / (SH_A + SH_B * log(res) + SH_C * pow(log(res), 3))) - 10.0;
	
	//Serial.print("temperature in kelvin: ");
	//Serial.println(T_K);
    
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