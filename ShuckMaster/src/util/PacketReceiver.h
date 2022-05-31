/*!
 * @file PacketReceiver.h
 * @author Sebastian S.
 * @brief Declaration for PacketReceiver
 */

#ifndef PACKET_RECEIVER_H_
#define PACKET_RECEIVER_H_

#include <Arduino.h>

/*!
 * @class PacketReceiver
 * @brief Receives bytes and recognizes packets.
 * 
 * Recognizes packets that start with a sync pattern in the form
 * [Length: 2 bytes] | [ID: 1 byte] | [Data: Length - 1]
 * Where Length is the byte size of the entire packet, excluding itself and the sync pattern.
 */
class PacketReceiver {
 public:
  /*!
   * @brief Initializes a PacketReceiver
   * @param buf An external buffer to use in building the packet
   * @param sync 32-bit sync pattern in little-endian to recognize before each packet
   */
  PacketReceiver(char *buf, uint32_t sync): _dataBuf(buf), _sync(sync),
                                            _pattern(0), _plen(0), _id(0),
                                            _toRead(0), _offset(0), _mode(Length) { }
  
  /*!
   * @brief Prepares the PacketReceiver to receive a new packet
   */
  void Begin();
  
  /*!
   * @brief Receive a single byte.
   * @param byte The byte to receive
   * @return True if receiving this byte completed a packet
   */
  bool AddByte(uint8_t byte);
  
  /*!
   * @return True if the current packet is completed
   */
  bool Completed();

  /*!
   * @return A pointer to a buffer containing a completed packet's data
   * @pre PacketReceiver::Completed()
   */
  const char *GetPacketData();
  
  /*!
   * @return The length of a completed packet's data length
   * @pre PacketReceiver::Completed()
   */
  uint16_t GetPacketDataLength();
  
  /*!
   * @return The ID of a completed packet
   * @pre PacketReceiver::Completed()
   */
  uint8_t GetPacketID();

  /*!
   * @brief Prints information about the current packet.
   * Information printed includes whether one is currently completed, and if so the id, length and data.
   */
  void PrintPacketInfo();
 private:
  enum ReadMode { Syncing, Length, ID, Data};
  ReadMode  _mode;

  uint32_t  _sync;        // sync pattern
  uint32_t  _pattern;     // last four bytes received
  
  char *    _dataBuf;     // data buf
  uint16_t  _plen;        // packet length
  uint8_t   _id;
  uint16_t  _toRead;
  uint16_t  _offset;
};

#endif
