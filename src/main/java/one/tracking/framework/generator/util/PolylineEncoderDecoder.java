/*
 * Copyright (C) 2019 HERE Europe B.V. Licensed under MIT, see full license in LICENSE
 * SPDX-License-Identifier: MIT License-Filename: LICENSE
 */
package one.tracking.framework.generator.util;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * The polyline encoding is a lossy compressed representation of a list of coordinate pairs or
 * coordinate triples. It achieves that by:
 * <p>
 * <ol>
 * <li>Reducing the decimal digits of each value.
 * <li>Encoding only the offset from the previous point.
 * <li>Using variable length for each coordinate delta.
 * <li>Using 64 URL-safe characters to display the result.
 * </ol>
 * <p>
 *
 * The advantage of this encoding are the following:
 * <p>
 * <ul>
 * <li>Output string is composed by only URL-safe characters
 * <li>Floating point precision is configurable
 * <li>It allows to encode a 3rd dimension with a given precision, which may be a level, altitude,
 * elevation or some other custom value
 * </ul>
 * <p>
 */
public class PolylineEncoderDecoder {

  /**
   * Header version A change in the version may affect the logic to encode and decode the rest of the
   * header and data
   */
  public static final byte FORMAT_VERSION = 1;

  // Base64 URL-safe characters
  public static final char[] ENCODING_TABLE =
      "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_".toCharArray();

  public static final int[] DECODING_TABLE = {
      62, -1, -1, 52, 53, 54, 55, 56, 57, 58, 59, 60, 61, -1, -1, -1, -1, -1, -1, -1,
      0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21,
      22, 23, 24, 25, -1, -1, -1, -1, 63, -1, 26, 27, 28, 29, 30, 31, 32, 33, 34, 35,
      36, 37, 38, 39, 40, 41, 42, 43, 44, 45, 46, 47, 48, 49, 50, 51
  };

  /**
   * Encode the list of coordinate triples.<BR>
   * <BR>
   * The third dimension value will be eligible for encoding only when ThirdDimension is other than
   * ABSENT. This is lossy compression based on precision accuracy.
   *
   * @param coordinates {@link List} of coordinate triples that to be encoded.
   * @param precision Floating point precision of the coordinate to be encoded.
   * @param thirdDimension {@link ThirdDimension} which may be a level, altitude, elevation or some
   *        other custom value
   * @param thirdDimPrecision Floating point precision for thirdDimension value
   * @return URL-safe encoded {@link String} for the given coordinates.
   */
  public static String encode(final List<LatLngZ> coordinates, final int precision, final ThirdDimension thirdDimension,
      final int thirdDimPrecision) {
    if (coordinates == null || coordinates.size() == 0) {
      throw new IllegalArgumentException("Invalid coordinates!");
    }
    if (thirdDimension == null) {
      throw new IllegalArgumentException("Invalid thirdDimension");
    }
    final Encoder enc = new Encoder(precision, thirdDimension, thirdDimPrecision);
    final Iterator<LatLngZ> iter = coordinates.iterator();
    while (iter.hasNext()) {
      enc.add(iter.next());
    }
    return enc.getEncoded();
  }

  /**
   * Decode the encoded input {@link String} to {@link List} of coordinate triples.<BR>
   * <BR>
   *
   * @param encoded URL-safe encoded {@link String}
   * @return {@link List} of coordinate triples that are decoded from input
   *
   * @see PolylineDecoder#getThirdDimension(String) getThirdDimension
   * @see LatLngZ
   */
  public static final List<LatLngZ> decode(final String encoded) {

    if (encoded == null || encoded.trim().isEmpty()) {
      throw new IllegalArgumentException("Invalid argument!");
    }
    final List<LatLngZ> result = new ArrayList<>();
    final Decoder dec = new Decoder(encoded);
    AtomicReference<Double> lat = new AtomicReference<>(0d);
    AtomicReference<Double> lng = new AtomicReference<>(0d);
    AtomicReference<Double> z = new AtomicReference<>(0d);

    while (dec.decodeOne(lat, lng, z)) {
      result.add(new LatLngZ(lat.get(), lng.get(), z.get()));
      lat = new AtomicReference<>(0d);
      lng = new AtomicReference<>(0d);
      z = new AtomicReference<>(0d);
    }
    return result;
  }

  /**
   * ThirdDimension type from the encoded input {@link String}
   *
   * @param encoded URL-safe encoded coordinate triples {@link String}
   * @return type of {@link ThirdDimension}
   */
  public static ThirdDimension getThirdDimension(final String encoded) {
    final AtomicInteger index = new AtomicInteger(0);
    final AtomicLong header = new AtomicLong(0);
    Decoder.decodeHeaderFromString(encoded, index, header);
    return ThirdDimension.fromNum((header.get() >> 4) & 7);
  }

  public byte getVersion() {
    return FORMAT_VERSION;
  }

  /*
   * Single instance for configuration, validation and encoding for an input request.
   */
  private static class Encoder {

    private final StringBuilder result;
    private final Converter latConveter;
    private final Converter lngConveter;
    private final Converter zConveter;
    private final ThirdDimension thirdDimension;

    public Encoder(final int precision, final ThirdDimension thirdDimension, final int thirdDimPrecision) {
      this.latConveter = new Converter(precision);
      this.lngConveter = new Converter(precision);
      this.zConveter = new Converter(thirdDimPrecision);
      this.thirdDimension = thirdDimension;
      this.result = new StringBuilder();
      encodeHeader(precision, this.thirdDimension.getNum(), thirdDimPrecision);
    }

    private void encodeHeader(final int precision, final int thirdDimensionValue, final int thirdDimPrecision) {
      /*
       * Encode the `precision`, `third_dim` and `third_dim_precision` into one encoded char
       */
      if (precision < 0 || precision > 15) {
        throw new IllegalArgumentException("precision out of range");
      }

      if (thirdDimPrecision < 0 || thirdDimPrecision > 15) {
        throw new IllegalArgumentException("thirdDimPrecision out of range");
      }

      if (thirdDimensionValue < 0 || thirdDimensionValue > 7) {
        throw new IllegalArgumentException("thirdDimensionValue out of range");
      }
      final long res = (thirdDimPrecision << 7) | (thirdDimensionValue << 4) | precision;
      Converter.encodeUnsignedVarint(PolylineEncoderDecoder.FORMAT_VERSION, this.result);
      Converter.encodeUnsignedVarint(res, this.result);
    }

    private void add(final double lat, final double lng) {
      this.latConveter.encodeValue(lat, this.result);
      this.lngConveter.encodeValue(lng, this.result);
    }

    private void add(final double lat, final double lng, final double z) {
      add(lat, lng);
      if (this.thirdDimension != ThirdDimension.ABSENT) {
        this.zConveter.encodeValue(z, this.result);
      }
    }

    private void add(final LatLngZ tuple) {
      if (tuple == null) {
        throw new IllegalArgumentException("Invalid LatLngZ tuple");
      }
      add(tuple.lat, tuple.lng, tuple.z);
    }

    private String getEncoded() {
      return this.result.toString();
    }
  }

  /*
   * Single instance for decoding an input request.
   */
  private static class Decoder {

    private final String encoded;
    private final AtomicInteger index;
    private final Converter latConveter;
    private final Converter lngConveter;
    private final Converter zConveter;

    private int precision;
    private int thirdDimPrecision;
    private ThirdDimension thirdDimension;


    public Decoder(final String encoded) {
      this.encoded = encoded;
      this.index = new AtomicInteger(0);
      decodeHeader();
      this.latConveter = new Converter(this.precision);
      this.lngConveter = new Converter(this.precision);
      this.zConveter = new Converter(this.thirdDimPrecision);
    }

    private boolean hasThirdDimension() {
      return this.thirdDimension != ThirdDimension.ABSENT;
    }

    private void decodeHeader() {
      final AtomicLong header = new AtomicLong(0);
      decodeHeaderFromString(this.encoded, this.index, header);
      this.precision = (int) (header.get() & 15); // we pick the first 3 bits only
      header.set(header.get() >> 4);
      this.thirdDimension = ThirdDimension.fromNum(header.get() & 7); // we pick the first 4 bits only
      this.thirdDimPrecision = (int) ((header.get() >> 3) & 15);
    }

    private static void decodeHeaderFromString(final String encoded, final AtomicInteger index,
        final AtomicLong header) {
      final AtomicLong value = new AtomicLong(0);

      // Decode the header version
      if (!Converter.decodeUnsignedVarint(encoded.toCharArray(), index, value)) {
        throw new IllegalArgumentException("Invalid encoding");
      }
      if (value.get() != FORMAT_VERSION) {
        throw new IllegalArgumentException("Invalid format version");
      }
      // Decode the polyline header
      if (!Converter.decodeUnsignedVarint(encoded.toCharArray(), index, value)) {
        throw new IllegalArgumentException("Invalid encoding");
      }
      header.set(value.get());
    }


    private boolean decodeOne(final AtomicReference<Double> lat,
        final AtomicReference<Double> lng,
        final AtomicReference<Double> z) {
      if (this.index.get() == this.encoded.length()) {
        return false;
      }
      if (!this.latConveter.decodeValue(this.encoded, this.index, lat)) {
        throw new IllegalArgumentException("Invalid encoding");
      }
      if (!this.lngConveter.decodeValue(this.encoded, this.index, lng)) {
        throw new IllegalArgumentException("Invalid encoding");
      }
      if (hasThirdDimension()) {
        if (!this.zConveter.decodeValue(this.encoded, this.index, z)) {
          throw new IllegalArgumentException("Invalid encoding");
        }
      }
      return true;
    }
  }

  // Decode a single char to the corresponding value
  private static int decodeChar(final char charValue) {
    final int pos = charValue - 45;
    if (pos < 0 || pos > 77) {
      return -1;
    }
    return DECODING_TABLE[pos];
  }

  /*
   * Stateful instance for encoding and decoding on a sequence of Coordinates part of an request.
   * Instance should be specific to type of coordinates (e.g. Lat, Lng) so that specific type delta is
   * computed for encoding. Lat0 Lng0 3rd0 (Lat1-Lat0) (Lng1-Lng0) (3rdDim1-3rdDim0)
   */
  public static class Converter {

    private long multiplier = 0;
    private long lastValue = 0;

    public Converter(final int precision) {
      setPrecision(precision);
    }

    private void setPrecision(final int precision) {
      this.multiplier = (long) Math.pow(10, Double.valueOf(precision));
    }

    private static void encodeUnsignedVarint(long value, final StringBuilder result) {
      while (value > 0x1F) {
        final byte pos = (byte) ((value & 0x1F) | 0x20);
        result.append(ENCODING_TABLE[pos]);
        value >>= 5;
      }
      result.append(ENCODING_TABLE[(byte) value]);
    }

    void encodeValue(final double value, final StringBuilder result) {
      /*
       * Round-half-up round(-1.4) --> -1 round(-1.5) --> -2 round(-2.5) --> -3
       */
      final long scaledValue = Math.round(Math.abs(value * this.multiplier)) * Math.round(Math.signum(value));
      long delta = scaledValue - this.lastValue;
      final boolean negative = delta < 0;

      this.lastValue = scaledValue;

      // make room on lowest bit
      delta <<= 1;

      // invert bits if the value is negative
      if (negative) {
        delta = ~delta;
      }
      encodeUnsignedVarint(delta, result);
    }

    private static boolean decodeUnsignedVarint(final char[] encoded,
        final AtomicInteger index,
        final AtomicLong result) {
      short shift = 0;
      long delta = 0;
      long value;

      while (index.get() < encoded.length) {
        value = decodeChar(encoded[index.get()]);
        if (value < 0) {
          return false;
        }
        index.incrementAndGet();
        delta |= (value & 0x1F) << shift;
        if ((value & 0x20) == 0) {
          result.set(delta);
          return true;
        } else {
          shift += 5;
        }
      }

      if (shift > 0) {
        return false;
      }
      return true;
    }

    // Decode single coordinate (say lat|lng|z) starting at index
    boolean decodeValue(final String encoded,
        final AtomicInteger index,
        final AtomicReference<Double> coordinate) {
      final AtomicLong delta = new AtomicLong();
      if (!decodeUnsignedVarint(encoded.toCharArray(), index, delta)) {
        return false;
      }
      if ((delta.get() & 1) != 0) {
        delta.set(~delta.get());
      }
      delta.set(delta.get() >> 1);
      this.lastValue += delta.get();
      coordinate.set(((double) this.lastValue / this.multiplier));
      return true;
    }
  }

  /**
   * 3rd dimension specification. Example a level, altitude, elevation or some other custom value.
   * ABSENT is default when there is no third dimension en/decoding required.
   */
  public enum ThirdDimension {
    ABSENT(0),
    LEVEL(1),
    ALTITUDE(2),
    ELEVATION(3),
    RESERVED1(4),
    RESERVED2(5),
    CUSTOM1(6),
    CUSTOM2(7);

    private final int num;

    ThirdDimension(final int num) {
      this.num = num;
    }

    public int getNum() {
      return this.num;
    }

    public static ThirdDimension fromNum(final long value) {
      for (final ThirdDimension dim : ThirdDimension.values()) {
        if (dim.getNum() == value) {
          return dim;
        }
      }
      return null;
    }
  }

  /**
   * Coordinate triple
   */
  public static class LatLngZ {
    public final double lat;
    public final double lng;
    public final double z;

    public LatLngZ(final double latitude, final double longitude) {
      this(latitude, longitude, 0);
    }

    public LatLngZ(final double latitude, final double longitude, final double thirdDimension) {
      this.lat = latitude;
      this.lng = longitude;
      this.z = thirdDimension;
    }

    @Override
    public String toString() {
      return "LatLngZ [lat=" + this.lat + ", lng=" + this.lng + ", z=" + this.z + "]";
    }

    @Override
    public boolean equals(final Object anObject) {
      if (this == anObject) {
        return true;
      }
      if (anObject instanceof LatLngZ) {
        final LatLngZ passed = (LatLngZ) anObject;
        if (passed.lat == this.lat && passed.lng == this.lng && passed.z == this.z) {
          return true;
        }
      }
      return false;
    }
  }
}
