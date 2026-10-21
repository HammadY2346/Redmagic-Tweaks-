package com.hammad.redmagictweaks.xposed;

import android.graphics.BlendMode;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.DashPathEffect;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;

import androidx.core.graphics.PathParser;

import java.util.Arrays;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Pure drawing code for CosmeticsHooks, ported from nubia-ly (BatteryRenderer / BatteryGeometry,
 * QsOutlineRenderer, QsTileEffects, NotificationEffects, BrightnessEffects, VolumeEffects).
 * Battery geometry adapted from RisingOS-Revived/android_frameworks_base (Apache 2.0,
 * Copyright 2006, 2017-2023 The Android Open Source Project; 2019 The LineageOS Project).
 * Nothing here touches host state: every method only draws on the canvas it is given.
 */
public final class CosmeticsArt {
    private CosmeticsArt() {}

    /** Runs the host's own draw of the decorated element. */
    interface NativeDraw { void draw() throws Throwable; }

    // ================================================================================================= battery

    static final int BATTERY_TEXT = 5, BATTERY_HIDDEN = 6;

    static final class Shape {
        final float width, height; final int direction; final String perimeter, fill, bolt, plus;
        Shape(float width, float height, int direction, String perimeter, String fill, String bolt, String plus) {
            this.width = width; this.height = height; this.direction = direction; this.perimeter = perimeter; this.fill = fill; this.bolt = bolt; this.plus = plus;
        }
    }

    private static final class Paths {
        final Shape geometry; final Path perimeter, fill, bolt, plus; final RectF fillBounds;
        Paths(Shape g, Path perimeter, Path fill, Path bolt, Path plus, RectF fillBounds) { geometry = g; this.perimeter = perimeter; this.fill = fill; this.bolt = bolt; this.plus = plus; this.fillBounds = fillBounds; }
    }
    private static final ConcurrentHashMap<Integer, Paths> cache = new ConcurrentHashMap<>();

    /** Original path geometry; rendering uses native REDMAGIC state and colours. direction: 0 bottom-up, 1 left-right, 2 right-left. */
    static Shape shape(int id) {
        switch (id) {
            case 1: return new Shape(12f,20f,0,"M3.5,2 v0 H1.33 C0.6,2 0,2.6 0,3.33 V13v5.67 C0,19.4 0.6,20 1.33,20 h9.33 C11.4,20 12,19.4 12,18.67 V13V3.33 C12,2.6 11.4,2 10.67,2 H8.5 V0 H3.5 z M2,18v-7V4h8v9v5H2L2,18z","M2,18 v-14 h8 v14 z","M5,17.5 V12 H3 L7,4.5 V10 h2 L5,17.5 z","M9,10l-2,0l0,-2l-2,0l0,2l-2,0l0,2l2,0l0,2l2,0l0,-2l2,0z");
            case 7: return new Shape(24.0f,12.0f,1,"M2.44,0.64L19.70,0.64A2.21 2.21 0 0 1 21.91,2.85L21.91,9.21A2.21 2.21 0 0 1 19.70,11.42L2.44,11.42A2.21 2.21 0 0 1 0.23,9.21L0.23,2.85A2.21 2.21 0 0 1 2.44,0.64zM0.87,3.59L0.87,8.41A2.28 2.28 0 0 0 3.15,10.69L18.98,10.69A2.28 2.28 0 0 0 21.26,8.41L21.26,3.59A2.28 2.28 0 0 0 18.98,1.31L3.15,1.31A2.28 2.28 0 0 0 0.87,3.59zM22.45,8.32Q25.10,5.95,22.42,3.62","M0.87,1.30L0.87,10.69A0.00 0.00 0 0 0 0.87,10.69L21.26,10.69A0.00 0.00 0 0 0 21.26,10.69L21.26,1.30A0.00 0.00 0 0 0 21.26,1.30L0.87,1.30A0.00 0.00 0 0 0 0.87,1.30z","M8.92,10.42L10.91,6.05L7.88,6.12L12.48,1.77L10.93,5.03L13.88,4.96z","M14.92,5.01L11.69,5.18L11.97,2.85L10.23,2.97L10.41,5.12L7.65,4.97L7.43,6.69L10.37,6.61L10.26,9.22L12.14,9.08L11.82,6.69L14.91,6.78z");
            case 8: return new Shape(24.0f,12.0f,2,"M3.77,0.80L21.85,0.80A1.97 1.97 0 0 1 23.82,2.77L23.82,9.31A1.97 1.97 0 0 1 21.85,11.27L3.77,11.27A1.97 1.97 0 0 1 1.81,9.31L1.81,2.77A1.97 1.97 0 0 1 3.77,0.80zM2.85,3.00L2.85,9.02A1.48 1.48 0 0 0 4.33,10.50L21.30,10.50A1.48 1.48 0 0 0 22.78,9.02L22.78,3.00A1.48 1.48 0 0 0 21.30,1.52L4.33,1.52A1.48 1.48 0 0 0 2.85,3.00zM1.26,7.97Q-1.14,5.86,1.29,3.81z","M2.82,1.39L2.82,10.63A0.00 0.00 0 0 0 2.82,10.63L22.88,10.63A0.00 0.00 0 0 0 22.88,10.63L22.88,1.39A0.00 0.00 0 0 0 22.88,1.39L2.82,1.39A0.00 0.00 0 0 0 2.82,1.39z","M10.81,10.30L12.84,6.06L9.76,6.13L14.43,1.90L12.86,5.07L15.85,5.00z","M8.90,5.05L12.19,5.21L11.90,2.95L13.67,3.07L13.48,5.16L16.28,5.01L16.51,6.68L13.53,6.60L13.64,9.13L11.73,9.00L12.05,6.68L8.92,6.76z");
            case 11: return new Shape(12.0f,20.0f,0,"M3.61,5.65L8.39,5.65A3.59 3.59 0 0 1 11.98,9.24L11.98,10.76A3.59 3.59 0 0 1 8.39,14.35L3.61,14.35A3.59 3.59 0 0 1 0.02,10.76L0.02,9.24A3.59 3.59 0 0 1 3.61,5.65zM1.32,9.13L1.32,10.87A2.17 2.17 0 0 0 3.50,13.04L8.50,13.04A2.17 2.17 0 0 0 10.68,10.87L10.68,9.13A2.17 2.17 0 0 0 8.50,6.96L3.50,6.96A2.17 2.17 0 0 0 1.32,9.13z","M2.25,8.00L9.75,8.00A0.00 0.00 0 0 1 9.75,8.00L9.75,12.00A0.00 0.00 0 0 1 9.75,12.00L2.25,12.00A0.00 0.00 0 0 1 2.25,12.00L2.25,8.00A0.00 0.00 0 0 1 2.25,8.00z","M5.61,10.76L5.60,12.15C5.58,12.42,5.95,12.49,6.14,12.15L7.25,10.09C7.44,9.66,7.07,9.24,6.39,9.24L6.40,7.85C6.42,7.58,6.05,7.51,5.86,7.85L4.75,9.91C4.56,10.34,4.93,10.76,5.61,10.76","M3.61,5.65L8.39,5.65A3.59 3.59 0 0 1 11.98,9.24L11.98,10.76A3.59 3.59 0 0 1 8.39,14.35L3.61,14.35A3.59 3.59 0 0 1 0.02,10.76L0.02,9.24A3.59 3.59 0 0 1 3.61,5.65zM1.32,9.13L1.32,10.87A2.17 2.17 0 0 0 3.50,13.04L8.50,13.04A2.17 2.17 0 0 0 10.68,10.87L10.68,9.13A2.17 2.17 0 0 0 8.50,6.96L3.50,6.96A2.17 2.17 0 0 0 1.32,9.13z");
            case 12: return new Shape(12.0f,20.0f,0,"M3.61,5.65L8.39,5.65A3.59 3.59 0 0 1 11.98,9.24L11.98,10.76A3.59 3.59 0 0 1 8.39,14.35L3.61,14.35A3.59 3.59 0 0 1 0.02,10.76L0.02,9.24A3.59 3.59 0 0 1 3.61,5.65zM1.32,9.13L1.32,10.87A2.17 2.17 0 0 0 3.50,13.04L8.50,13.04A2.17 2.17 0 0 0 10.68,10.87L10.68,9.13A2.17 2.17 0 0 0 8.50,6.96L3.50,6.96A2.17 2.17 0 0 0 1.32,9.13z","M6.00,7.72L6.00,7.72A0.65 0.65 0 0 1 6.65,8.37L6.65,11.63A0.65 0.65 0 0 1 6.00,12.28L6.00,12.28A0.65 0.65 0 0 1 5.35,11.63L5.35,8.37A0.65 0.65 0 0 1 6.00,7.72z","M5.61,10.76L5.60,12.15C5.58,12.42,5.95,12.49,6.14,12.15L7.25,10.09C7.44,9.66,7.07,9.24,6.39,9.24L6.40,7.85C6.42,7.58,6.05,7.51,5.86,7.85L4.75,9.91C4.56,10.34,4.93,10.76,5.61,10.76","M1.71,6.00L8.79,6.00A1.71 1.71 0 0 1 10.50,7.71L10.50,12.29A1.71 1.71 0 0 1 8.79,14.00L1.71,14.00A1.71 1.71 0 0 1 0.00,12.29L0.00,7.71A1.71 1.71 0 0 1 1.71,6.00zM11.00,9.25L11.00,10.75A0.50 0.50 0 0 0 11.50,11.25L11.50,11.25A0.50 0.50 0 0 0 12.00,10.75L12.00,9.25A0.50 0.50 0 0 0 11.50,8.75L11.50,8.75A0.50 0.50 0 0 0 11.00,9.25zM1.00,8.00L1.00,12.00A1.00 1.00 0 0 0 2.00,13.00L8.50,13.00A1.00 1.00 0 0 0 9.50,12.00L9.50,8.00A1.00 1.00 0 0 0 8.50,7.00L2.00,7.00A1.00 1.00 0 0 0 1.00,8.00z");
            case 13: return new Shape(12.0f,20.0f,0,"M1.18,17.23L1.18,4.75A2.77 2.77 0 0 1 3.95,1.98L8.05,1.98A2.77 2.77 0 0 1 10.82,4.75L10.82,17.23A2.77 2.77 0 0 1 8.05,20.00L3.95,20.00A2.77 2.77 0 0 1 1.18,17.23zM4.25,19.07L7.75,19.07A2.14 2.14 0 0 0 9.88,16.93L9.88,5.05A2.14 2.14 0 0 0 7.75,2.91L4.25,2.91A2.14 2.14 0 0 0 2.12,5.05L2.12,16.93A2.14 2.14 0 0 0 4.25,19.07zM7.71,1.05C6.78,-0.35,5.22,-0.35,4.29,1.05L7.71,1.05z","M2.88,16.88L2.88,5.20A1.33 1.33 0 0 1 4.20,3.88L7.80,3.88A1.33 1.33 0 0 1 9.13,5.20L9.13,16.88A1.33 1.33 0 0 1 7.80,18.21L4.20,18.21A1.33 1.33 0 0 1 2.88,16.88z","M6.84,11.51L6.84,13.68Q6.77,14.26,6.26,13.90L1.79,10.09C1.47,9.77,1.69,9.11,2.45,9.44L5.16,10.49L5.16,8.32Q5.23,7.74,5.74,8.10L10.21,11.91C10.53,12.23,10.31,12.89,9.55,12.56L6.84,11.51z","M1.18,17.23L1.18,4.75A2.77 2.77 0 0 1 3.95,1.98L8.05,1.98A2.77 2.77 0 0 1 10.82,4.75L10.82,17.23A2.77 2.77 0 0 1 8.05,20.00L3.95,20.00A2.77 2.77 0 0 1 1.18,17.23zM4.25,19.07L7.75,19.07A2.14 2.14 0 0 0 9.88,16.93L9.88,5.05A2.14 2.14 0 0 0 7.75,2.91L4.25,2.91A2.14 2.14 0 0 0 2.12,5.05L2.12,16.93A2.14 2.14 0 0 0 4.25,19.07zM7.71,1.05C6.78,-0.35,5.22,-0.35,4.29,1.05L7.71,1.05z");
            case 14: return new Shape(12.0f,20.0f,0,"M4.39,0.15C4.1,0.28,4.02,0.35,3.89,0.59C3.68,0.98,3.78,1.55,4.11,1.83C4.4,2.09,4.55,2.1,6.44,2.1C8.33,2.1,8.47,2.09,8.76,1.83C9.1,1.55,9.2,0.98,8.98,0.59C8.85,0.35,8.78,0.28,8.49,0.15C8.3,0.06,8.15,0.05,6.44,0.05C4.72,0.05,4.58,0.06,4.39,0.15ZM4.39,0.15M3.18,2.6C2.68,2.74,2.34,2.95,1.92,3.35C1.35,3.89,1.04,4.54,1.04,5.16L1.04,5.4L3.59,5.4L3.86,5.13C4.39,4.61,4.64,4.55,6.46,4.55C8.29,4.55,8.54,4.61,9.07,5.13L9.34,5.4L11.94,5.4L11.94,5.17C11.94,4.55,11.62,3.89,11.06,3.35C10.63,2.93,10.3,2.74,9.77,2.59C9.45,2.5,9.23,2.5,6.46,2.5C3.76,2.5,3.48,2.52,3.18,2.6ZM3.18,2.6M0.93,11L0.93,15.65L3.27,15.65L3.27,6.35L0.93,6.35ZM0.93,11M9.66,11L9.66,15.65L11.99,15.65L11.99,6.35L9.66,6.35ZM9.66,11M1.04,16.86C1.04,17.45,1.37,18.11,1.92,18.65C2.35,19.06,2.76,19.3,3.25,19.43C3.7,19.54,9.28,19.54,9.73,19.43C10.23,19.3,10.63,19.06,11.06,18.65C11.61,18.11,11.94,17.45,11.94,16.86L11.94,16.65L9.35,16.65L9.07,16.92C8.52,17.45,8.29,17.5,6.46,17.5C4.64,17.5,4.4,17.45,3.86,16.92L3.58,16.65L1.04,16.65ZM1.04,16.86","M5.32,6.04C5.09,6.15,4.98,6.25,4.88,6.46C4.78,6.64,4.78,6.82,4.78,11.08C4.78,15.32,4.78,15.52,4.88,15.69C5.11,16.14,5.32,16.2,6.46,16.2C7.61,16.2,7.82,16.14,8.05,15.69C8.14,15.52,8.15,15.32,8.15,11.08C8.15,6.82,8.15,6.64,8.05,6.46C7.94,6.25,7.84,6.14,7.59,6.03C7.36,5.91,5.55,5.91,5.32,6.04ZM5.32,6.04","M5,17.5 V12 H3 L7,4.5 V10 h2 L5,17.5 z","M4.39,0.15C4.1,0.28,4.02,0.35,3.89,0.59C3.68,0.98,3.78,1.55,4.11,1.83C4.4,2.09,4.55,2.1,6.44,2.1C8.33,2.1,8.47,2.09,8.76,1.83C9.1,1.55,9.2,0.98,8.98,0.59C8.85,0.35,8.78,0.28,8.49,0.15C8.3,0.06,8.15,0.05,6.44,0.05C4.72,0.05,4.58,0.06,4.39,0.15ZM4.39,0.15M3.18,2.6C2.68,2.74,2.34,2.95,1.92,3.35C1.35,3.89,1.04,4.54,1.04,5.16L1.04,5.4L3.59,5.4L3.86,5.13C4.39,4.61,4.64,4.55,6.46,4.55C8.29,4.55,8.54,4.61,9.07,5.13L9.34,5.4L11.94,5.4L11.94,5.17C11.94,4.55,11.62,3.89,11.06,3.35C10.63,2.93,10.3,2.74,9.77,2.59C9.45,2.5,9.23,2.5,6.46,2.5C3.76,2.5,3.48,2.52,3.18,2.6ZM3.18,2.6M0.93,11L0.93,15.65L3.27,15.65L3.27,6.35L0.93,6.35ZM0.93,11M9.66,11L9.66,15.65L11.99,15.65L11.99,6.35L9.66,6.35ZM9.66,11M1.04,16.86C1.04,17.45,1.37,18.11,1.92,18.65C2.35,19.06,2.76,19.3,3.25,19.43C3.7,19.54,9.28,19.54,9.73,19.43C10.23,19.3,10.63,19.06,11.06,18.65C11.61,18.11,11.94,17.45,11.94,16.86L11.94,16.65L9.35,16.65L9.07,16.92C8.52,17.45,8.29,17.5,6.46,17.5C4.64,17.5,4.4,17.45,3.86,16.92L3.58,16.65L1.04,16.65ZM1.04,16.86");
            case 15: return new Shape(12.0f,20.0f,0,"M2.85,4.00L9.15,4.00A2.85 2.85 0 0 1 12.00,6.85L12.00,13.15A2.85 2.85 0 0 1 9.15,16.00L2.85,16.00A2.85 2.85 0 0 1 -0.00,13.15L-0.00,6.85A2.85 2.85 0 0 1 2.85,4.00zM8.00,4.00L4.00,4.00C4.00,3.50,4.00,3.00,4.50,3.00L7.50,3.00C8.00,3.00,8.00,3.50,8.00,4.00zM2.49,12.25L2.49,12.91A0.66 0.66 0 0 0 3.15,13.57L6.24,13.57A0.66 0.66 0 0 0 6.90,12.91L6.90,12.25A0.66 0.66 0 0 0 6.24,11.59L3.15,11.59A0.66 0.66 0 0 0 2.49,12.25zM2.48,9.71L2.48,10.37A0.66 0.66 0 0 0 3.14,11.03L6.24,11.03A0.66 0.66 0 0 0 6.90,10.37L6.90,9.71A0.66 0.66 0 0 0 6.24,9.05L3.14,9.05A0.66 0.66 0 0 0 2.48,9.71zM1.80,6.85L1.80,13.15A1.05 1.05 0 0 0 2.85,14.20L9.15,14.20A1.05 1.05 0 0 0 10.20,13.15L10.20,6.85A1.05 1.05 0 0 0 9.15,5.80L2.85,5.80A1.05 1.05 0 0 0 1.80,6.85zM2.49,7.18L2.49,7.84A0.66 0.66 0 0 0 3.15,8.50L6.24,8.50A0.66 0.66 0 0 0 6.90,7.84L6.90,7.18A0.66 0.66 0 0 0 6.24,6.52L3.15,6.52A0.66 0.66 0 0 0 2.49,7.18z","M8.28,6.53L8.89,6.53A0.61 0.61 0 0 1 9.50,7.14L9.50,12.96A0.61 0.61 0 0 1 8.89,13.57L8.28,13.57A0.61 0.61 0 0 1 7.67,12.96L7.67,7.14A0.61 0.61 0 0 1 8.28,6.53z","M4.26,10.66L4.25,11.86C4.23,12.10,4.62,12.16,4.82,11.86L5.97,10.08C6.17,9.71,5.79,9.35,5.08,9.34L5.08,8.14C5.10,7.90,4.72,7.84,4.52,8.14L3.36,9.92C3.16,10.29,3.55,10.65,4.26,10.66","M2.85,4.00L9.15,4.00A2.85 2.85 0 0 1 12.00,6.85L12.00,13.15A2.85 2.85 0 0 1 9.15,16.00L2.85,16.00A2.85 2.85 0 0 1 -0.00,13.15L-0.00,6.85A2.85 2.85 0 0 1 2.85,4.00zM8.00,4.00L4.00,4.00C4.00,3.50,4.00,3.00,4.50,3.00L7.50,3.00C8.00,3.00,8.00,3.50,8.00,4.00zM2.49,12.25L2.49,12.91A0.66 0.66 0 0 0 3.15,13.57L6.24,13.57A0.66 0.66 0 0 0 6.90,12.91L6.90,12.25A0.66 0.66 0 0 0 6.24,11.59L3.15,11.59A0.66 0.66 0 0 0 2.49,12.25zM2.48,9.71L2.48,10.37A0.66 0.66 0 0 0 3.14,11.03L6.24,11.03A0.66 0.66 0 0 0 6.90,10.37L6.90,9.71A0.66 0.66 0 0 0 6.24,9.05L3.14,9.05A0.66 0.66 0 0 0 2.48,9.71zM1.80,6.85L1.80,13.15A1.05 1.05 0 0 0 2.85,14.20L9.15,14.20A1.05 1.05 0 0 0 10.20,13.15L10.20,6.85A1.05 1.05 0 0 0 9.15,5.80L2.85,5.80A1.05 1.05 0 0 0 1.80,6.85zM2.49,7.18L2.49,7.84A0.66 0.66 0 0 0 3.15,8.50L6.24,8.50A0.66 0.66 0 0 0 6.90,7.84L6.90,7.18A0.66 0.66 0 0 0 6.24,6.52L3.15,6.52A0.66 0.66 0 0 0 2.49,7.18z");
            case 16: return new Shape(24.0f,12.0f,1,"M20.74,6L20.74,9.01L20.83,9C21.17,8.98,21.45,8.88,21.74,8.69C21.89,8.59,21.98,8.52,22.11,8.38C22.43,8.06,22.65,7.68,22.81,7.19C23.22,5.94,22.93,4.48,22.13,3.64C21.75,3.25,21.32,3.04,20.84,3L20.74,3L20.74,6Z,M2.74,0.01C2.41,0.04,2.08,0.12,1.79,0.25C1.34,0.44,0.92,0.76,0.61,1.16C0.26,1.62,0.03,2.19,-0.01,2.78C-0.02,2.95,-0.02,9.07,-0.01,9.23C0.02,9.62,0.12,9.98,0.29,10.33C0.59,10.92,1.03,11.38,1.61,11.67C1.87,11.81,2.13,11.9,2.42,11.95C2.7,12.01,2.21,12,9.99,12C15.93,12,17.22,12,17.31,11.9C18.35,11.87,19.23,11.25,19.68,10.33C19.84,10,19.93,9.68,19.98,9.33C19.99,9.16,19.99,2.85,19.98,2.68C19.93,2.32,19.84,2,19.67,1.66C19.44,1.18,19.09,0.79,18.64,0.49C18.21,0.21,17.75,0.06,17.22,0.01C17.08,-0,2.87,-0,2.74,0.01ZM17.03,1.26C17.67,1.33,18.21,1.7,18.51,2.26C18.58,2.39,18.64,2.54,18.67,2.68C18.74,2.93,18.73,2.74,18.73,6C18.73,7.97,18.73,9,18.72,9.06C18.64,9.76,18.19,10.36,17.54,10.62C17.42,10.67,17.3,10.7,17.15,10.73L17.03,10.75L9.98,10.75L2.93,10.75L2.8,10.73C1.95,10.58,1.32,9.9,1.23,9.05C1.22,8.97,1.22,8.02,1.22,5.93L1.22,2.93L1.25,2.82C1.35,2.28,1.64,1.85,2.09,1.56C2.32,1.4,2.63,1.29,2.91,1.26C3.03,1.25,16.91,1.25,17.03,1.26Z","M3.32,1.76C3.15,1.77,3.01,1.8,2.86,1.85C2.26,2.07,1.84,2.59,1.75,3.22C1.73,3.35,1.73,8.67,1.75,8.8C1.86,9.49,2.35,10.04,3.03,10.21C3.22,10.25,2.84,10.25,9.99,10.25C15.54,10.25,16.7,10.25,16.78,10.24C17.29,10.16,17.73,9.87,18,9.43C18.1,9.26,18.19,9.02,18.22,8.82C18.24,8.72,18.24,8.52,18.24,6.01C18.24,3.8,18.24,3.29,18.22,3.22C18.13,2.59,17.71,2.07,17.11,1.85C17.01,1.82,16.93,1.8,16.81,1.77C16.73,1.76,16.2,1.75,10.04,1.75C6.36,1.75,3.34,1.75,3.32,1.76Z","M7.43,6.77L9.68,6.77L8.51,9.81C8.34,10.26,8.8,10.49,9.1,10.13L12.75,5.78C12.82,5.7,12.86,5.61,12.86,5.52C12.86,5.35,12.73,5.23,12.54,5.23L10.29,5.23L11.46,2.19C11.63,1.75,11.17,1.52,10.88,1.87L7.23,6.21C7.15,6.3,7.11,6.39,7.11,6.48C7.11,6.65,7.25,6.77,7.43,6.77Z","M20.74,6L20.74,9.01L20.83,9C21.17,8.98,21.45,8.88,21.74,8.69C21.89,8.59,21.98,8.52,22.11,8.38C22.43,8.06,22.65,7.68,22.81,7.19C23.22,5.94,22.93,4.48,22.13,3.64C21.75,3.25,21.32,3.04,20.84,3L20.74,3L20.74,6Z,M2.74,0.01C2.41,0.04,2.08,0.12,1.79,0.25C1.34,0.44,0.92,0.76,0.61,1.16C0.26,1.62,0.03,2.19,-0.01,2.78C-0.02,2.95,-0.02,9.07,-0.01,9.23C0.02,9.62,0.12,9.98,0.29,10.33C0.59,10.92,1.03,11.38,1.61,11.67C1.87,11.81,2.13,11.9,2.42,11.95C2.7,12.01,2.21,12,9.99,12C15.93,12,17.22,12,17.31,11.9C18.35,11.87,19.23,11.25,19.68,10.33C19.84,10,19.93,9.68,19.98,9.33C19.99,9.16,19.99,2.85,19.98,2.68C19.93,2.32,19.84,2,19.67,1.66C19.44,1.18,19.09,0.79,18.64,0.49C18.21,0.21,17.75,0.06,17.22,0.01C17.08,-0,2.87,-0,2.74,0.01ZM17.03,1.26C17.67,1.33,18.21,1.7,18.51,2.26C18.58,2.39,18.64,2.54,18.67,2.68C18.74,2.93,18.73,2.74,18.73,6C18.73,7.97,18.73,9,18.72,9.06C18.64,9.76,18.19,10.36,17.54,10.62C17.42,10.67,17.3,10.7,17.15,10.73L17.03,10.75L9.98,10.75L2.93,10.75L2.8,10.73C1.95,10.58,1.32,9.9,1.23,9.05C1.22,8.97,1.22,8.02,1.22,5.93L1.22,2.93L1.25,2.82C1.35,2.28,1.64,1.85,2.09,1.56C2.32,1.4,2.63,1.29,2.91,1.26C3.03,1.25,16.91,1.25,17.03,1.26Z");
            case 17: return new Shape(24.0f,12.0f,2,"M2.25,6L2.25,9.01L2.16,9C1.82,8.97,1.54,8.88,1.25,8.69C1.1,8.59,1.01,8.52,0.88,8.38C0.56,8.06,0.34,7.68,0.18,7.18C-0.22,5.94,0.06,4.47,0.86,3.64C1.24,3.25,1.67,3.04,2.15,3L2.25,3L2.25,6Z,M20.26,0.01C20.58,0.04,20.91,0.12,21.2,0.25C21.65,0.44,22.07,0.76,22.38,1.16C22.74,1.62,22.96,2.19,23,2.78C23.02,2.95,23.02,9.07,23,9.23C22.97,9.62,22.87,9.98,22.7,10.33C22.41,10.92,21.96,11.37,21.38,11.67C21.12,11.81,20.86,11.9,20.58,11.95C20.29,12,20.78,12,13,12C7.06,12,5.77,12,5.68,11.99C4.64,11.87,3.76,11.25,3.31,10.33C3.15,10,3.06,9.68,3.02,9.32C3,9.15,3,2.85,3.02,2.68C3.06,2.32,3.15,2,3.32,1.66C3.56,1.18,3.9,0.79,4.35,0.49C4.78,0.21,5.24,0.06,5.77,0.01C5.91,-0,20.12,-0,20.26,0.01ZM5.96,1.26C5.33,1.33,4.78,1.7,4.48,2.26C4.41,2.39,4.35,2.54,4.32,2.68C4.26,2.93,4.26,2.74,4.26,6C4.26,7.97,4.26,9,4.27,9.06C4.35,9.76,4.8,10.36,5.45,10.62C5.57,10.67,5.7,10.7,5.84,10.73L5.96,10.75L13.01,10.75L20.06,10.75L20.19,10.73C21.04,10.57,21.68,9.89,21.76,9.04C21.77,8.97,21.77,8.02,21.77,5.93L21.77,2.92L21.74,2.81C21.64,2.28,21.35,1.85,20.91,1.56C20.67,1.4,20.37,1.29,20.08,1.26C19.96,1.25,6.08,1.25,5.96,1.26Z","M19.67,1.75C19.84,1.77,19.98,1.8,20.13,1.85C20.73,2.07,21.15,2.58,21.24,3.21C21.26,3.35,21.26,8.67,21.24,8.79C21.13,9.49,20.64,10.04,19.96,10.2C19.77,10.25,20.15,10.25,13,10.25C7.45,10.25,6.29,10.25,6.21,10.24C5.7,10.16,5.26,9.87,4.99,9.42C4.89,9.26,4.8,9.02,4.77,8.82C4.75,8.72,4.75,8.52,4.75,6.01C4.75,3.8,4.76,3.29,4.77,3.21C4.86,2.58,5.28,2.07,5.88,1.85C5.98,1.81,6.06,1.79,6.18,1.77C6.26,1.76,6.79,1.75,12.95,1.75C16.63,1.75,19.65,1.75,19.67,1.75Z","M10.45,6.77L12.7,6.77L11.53,9.81C11.36,10.26,11.82,10.49,12.12,10.13L15.77,5.78C15.84,5.7,15.88,5.61,15.88,5.52C15.88,5.35,15.75,5.23,15.56,5.23L13.31,5.23L14.48,2.19C14.65,1.75,14.19,1.51,13.9,1.87L10.25,6.2C10.17,6.3,10.13,6.39,10.13,6.48C10.13,6.65,10.27,6.77,10.45,6.77Z","M2.25,6L2.25,9.01L2.16,9C1.82,8.97,1.54,8.88,1.25,8.69C1.1,8.59,1.01,8.52,0.88,8.38C0.56,8.06,0.34,7.68,0.18,7.18C-0.22,5.94,0.06,4.47,0.86,3.64C1.24,3.25,1.67,3.04,2.15,3L2.25,3L2.25,6Z,M20.26,0.01C20.58,0.04,20.91,0.12,21.2,0.25C21.65,0.44,22.07,0.76,22.38,1.16C22.74,1.62,22.96,2.19,23,2.78C23.02,2.95,23.02,9.07,23,9.23C22.97,9.62,22.87,9.98,22.7,10.33C22.41,10.92,21.96,11.37,21.38,11.67C21.12,11.81,20.86,11.9,20.58,11.95C20.29,12,20.78,12,13,12C7.06,12,5.77,12,5.68,11.99C4.64,11.87,3.76,11.25,3.31,10.33C3.15,10,3.06,9.68,3.02,9.32C3,9.15,3,2.85,3.02,2.68C3.06,2.32,3.15,2,3.32,1.66C3.56,1.18,3.9,0.79,4.35,0.49C4.78,0.21,5.24,0.06,5.77,0.01C5.91,-0,20.12,-0,20.26,0.01ZM5.96,1.26C5.33,1.33,4.78,1.7,4.48,2.26C4.41,2.39,4.35,2.54,4.32,2.68C4.26,2.93,4.26,2.74,4.26,6C4.26,7.97,4.26,9,4.27,9.06C4.35,9.76,4.8,10.36,5.45,10.62C5.57,10.67,5.7,10.7,5.84,10.73L5.96,10.75L13.01,10.75L20.06,10.75L20.19,10.73C21.04,10.57,21.68,9.89,21.76,9.04C21.77,8.97,21.77,8.02,21.77,5.93L21.77,2.92L21.74,2.81C21.64,2.28,21.35,1.85,20.91,1.56C20.67,1.4,20.37,1.29,20.08,1.26C19.96,1.25,6.08,1.25,5.96,1.26Z");
            case 18: return new Shape(24.0f,12.0f,1,"M6.10,0.80L15.78,0.80A5.18 5.18 0 0 1 20.96,5.98L20.96,5.98A5.18 5.18 0 0 1 15.78,11.16L6.10,11.16A5.18 5.18 0 0 1 0.92,5.98L0.92,5.98A5.18 5.18 0 0 1 6.10,0.80zM2.42,5.98L2.42,5.98A3.67 3.67 0 0 0 6.09,9.65L15.78,9.65A3.67 3.67 0 0 0 19.45,5.98L19.45,5.98A3.67 3.67 0 0 0 15.78,2.31L6.09,2.31A3.67 3.67 0 0 0 2.42,5.98zM21.70,5.05L21.70,7.02A0.92 0.92 0 0 0 22.62,7.94L22.62,7.94A0.92 0.92 0 0 0 23.54,7.02L23.54,5.05A0.92 0.92 0 0 0 22.62,4.13L22.62,4.13A0.92 0.92 0 0 0 21.70,5.05z","M4.10,4.45L4.10,7.51A0.00 0.00 0 0 0 4.10,7.51L17.78,7.51A0.00 0.00 0 0 0 17.78,7.51L17.78,4.45A0.00 0.00 0 0 0 17.78,4.45L4.10,4.45A0.00 0.00 0 0 0 4.10,4.45z","M10.81,10.30L12.84,6.06L9.76,6.13L14.43,1.90L12.86,5.07L15.85,5.00z","M6.10,0.80L15.78,0.80A5.18 5.18 0 0 1 20.96,5.98L20.96,5.98A5.18 5.18 0 0 1 15.78,11.16L6.10,11.16A5.18 5.18 0 0 1 0.92,5.98L0.92,5.98A5.18 5.18 0 0 1 6.10,0.80zM2.42,5.98L2.42,5.98A3.67 3.67 0 0 0 6.09,9.65L15.78,9.65A3.67 3.67 0 0 0 19.45,5.98L19.45,5.98A3.67 3.67 0 0 0 15.78,2.31L6.09,2.31A3.67 3.67 0 0 0 2.42,5.98zM21.70,5.05L21.70,7.02A0.92 0.92 0 0 0 22.62,7.94L22.62,7.94A0.92 0.92 0 0 0 23.54,7.02L23.54,5.05A0.92 0.92 0 0 0 22.62,4.13L22.62,4.13A0.92 0.92 0 0 0 21.70,5.05z");
            case 19: return new Shape(24.0f,12.0f,2,"M8.22,0.80L17.90,0.80A5.18 5.18 0 0 1 23.08,5.98L23.08,5.98A5.18 5.18 0 0 1 17.90,11.16L8.22,11.16A5.18 5.18 0 0 1 3.04,5.98L3.04,5.98A5.18 5.18 0 0 1 8.22,0.80zM0.46,5.05L0.46,7.02A0.92 0.92 0 0 0 1.38,7.94L1.38,7.94A0.92 0.92 0 0 0 2.30,7.02L2.30,5.05A0.92 0.92 0 0 0 1.38,4.13L1.38,4.13A0.92 0.92 0 0 0 0.46,5.05zM4.55,5.98L4.55,5.98A3.67 3.67 0 0 0 8.22,9.65L17.91,9.65A3.67 3.67 0 0 0 21.58,5.98L21.58,5.98A3.67 3.67 0 0 0 17.91,2.31L8.22,2.31A3.67 3.67 0 0 0 4.55,5.98z","M4.10,4.45L4.10,7.51A0.00 0.00 0 0 0 4.10,7.51L17.78,7.51A0.00 0.00 0 0 0 17.78,7.51L17.78,4.45A0.00 0.00 0 0 0 17.78,4.45L4.10,4.45A0.00 0.00 0 0 0 4.10,4.45z","M10.81,10.30L12.84,6.06L9.76,6.13L14.43,1.90L12.86,5.07L15.85,5.00z","M8.22,0.80L17.90,0.80A5.18 5.18 0 0 1 23.08,5.98L23.08,5.98A5.18 5.18 0 0 1 17.90,11.16L8.22,11.16A5.18 5.18 0 0 1 3.04,5.98L3.04,5.98A5.18 5.18 0 0 1 8.22,0.80zM0.46,5.05L0.46,7.02A0.92 0.92 0 0 0 1.38,7.94L1.38,7.94A0.92 0.92 0 0 0 2.30,7.02L2.30,5.05A0.92 0.92 0 0 0 1.38,4.13L1.38,4.13A0.92 0.92 0 0 0 0.46,5.05zM4.55,5.98L4.55,5.98A3.67 3.67 0 0 0 8.22,9.65L17.91,9.65A3.67 3.67 0 0 0 21.58,5.98L21.58,5.98A3.67 3.67 0 0 0 17.91,2.31L8.22,2.31A3.67 3.67 0 0 0 4.55,5.98z");
            case 20: return new Shape(24.0f,12.0f,1,"M3.79,0.78L18.21,0.78A2.82 2.82 0 0 1 21.03,3.60L21.03,8.40A2.82 2.82 0 0 1 18.21,11.22L3.79,11.22A2.82 2.82 0 0 1 0.97,8.40L0.97,3.60A2.82 2.82 0 0 1 3.79,0.78zM21.82,7.59C23.43,7.11,23.43,4.89,21.82,4.41L21.82,7.59zM2.00,3.75L2.00,8.25A2.00 2.00 0 0 0 4.00,10.25L18.00,10.25A2.00 2.00 0 0 0 20.00,8.25L20.00,3.75A2.00 2.00 0 0 0 18.00,1.75L4.00,1.75A2.00 2.00 0 0 0 2.00,3.75z","M4.32,2.63L17.68,2.63A1.45 1.45 0 0 1 19.13,4.07L19.12,7.93A1.45 1.45 0 0 1 17.68,9.37L4.32,9.38A1.45 1.45 0 0 1 2.87,7.93L2.87,4.07A1.45 1.45 0 0 1 4.32,2.63z","M10.39,6.94L7.95,6.94Q7.30,6.86,7.70,6.29L11.98,1.28C12.34,0.91,13.07,1.16,12.71,2.02L11.53,5.06L13.97,5.06Q14.62,5.14,14.21,5.71L9.94,10.72C9.58,11.09,8.84,10.84,9.21,9.98L10.39,6.94z","M3.79,0.78L18.21,0.78A2.82 2.82 0 0 1 21.03,3.60L21.03,8.40A2.82 2.82 0 0 1 18.21,11.22L3.79,11.22A2.82 2.82 0 0 1 0.97,8.40L0.97,3.60A2.82 2.82 0 0 1 3.79,0.78zM21.82,7.59C23.43,7.11,23.43,4.89,21.82,4.41L21.82,7.59zM2.00,3.75L2.00,8.25A2.00 2.00 0 0 0 4.00,10.25L18.00,10.25A2.00 2.00 0 0 0 20.00,8.25L20.00,3.75A2.00 2.00 0 0 0 18.00,1.75L4.00,1.75A2.00 2.00 0 0 0 2.00,3.75z");
            case 21: return new Shape(24.0f,12.0f,1,"M3.13,0.36L18.71,0.36A3.04 3.04 0 0 1 21.76,3.40L21.76,8.60A3.04 3.04 0 0 1 18.71,11.64L3.13,11.64A3.04 3.04 0 0 1 0.08,8.60L0.08,3.40A3.04 3.04 0 0 1 3.13,0.36zM22.61,7.72C24.35,7.19,24.35,4.81,22.61,4.28L22.61,7.72z","M3.13,0.36L18.71,0.36A3.04 3.04 0 0 1 21.76,3.40L21.76,8.60A3.04 3.04 0 0 1 18.71,11.64L3.13,11.64A3.04 3.04 0 0 1 0.08,8.60L0.08,3.40A3.04 3.04 0 0 1 3.13,0.36zM22.61,7.72C24.35,7.19,24.35,4.81,22.61,4.28L22.61,7.72z","M17.96,5.25L19.91,5.25Q20.43,5.31,20.10,5.77L16.69,9.77C16.39,10.06,15.81,9.87,16.10,9.18L17.04,6.75L15.09,6.75Q14.57,6.69,14.90,6.23L18.31,2.23C18.61,1.94,19.19,2.13,18.90,2.82L17.96,5.25z","M3.13,0.36L18.71,0.36A3.04 3.04 0 0 1 21.76,3.40L21.76,8.60A3.04 3.04 0 0 1 18.71,11.64L3.13,11.64A3.04 3.04 0 0 1 0.08,8.60L0.08,3.40A3.04 3.04 0 0 1 3.13,0.36zM22.61,7.72C24.35,7.19,24.35,4.81,22.61,4.28L22.61,7.72z");
            case 22: return new Shape(20.0f,12.0f,0,"M15.50,0.50C13.50,0.33,11.00,0.33,8.50,0.50C8.00,-0.21,6.00,-0.21,5.63,1.00C5.17,2.50,4.33,2.33,4.00,4.33C3.75,5.50,3.75,7.50,4.00,8.43C4.25,9.50,5.50,12.00,8.95,12.00L15.00,12.00C18.45,12.00,19.70,9.50,19.95,8.43C20.20,7.50,20.20,5.50,19.95,4.33C19.62,2.33,18.79,2.50,18.33,1.00C18.00,-0.21,16.00,-0.21,15.50,0.50zM15.25,10.50C12.50,10.57,11.50,10.57,8.75,10.50C7.50,10.40,6.00,9.75,5.50,7.75Q5.12,6.00,5.50,4.50C6.00,3.00,7.50,2.10,8.75,2.00C11.00,1.88,13.00,1.88,15.25,2.00C16.50,2.10,18.00,3.00,18.50,4.50Q18.88,6.00,18.50,7.75C18.00,9.75,16.50,10.40,15.25,10.50z","M9.10,1.55L14.90,1.55A4.10 4.10 0 0 1 19.00,5.64L19.00,6.86A4.10 4.10 0 0 1 14.90,10.95L9.10,10.95A4.10 4.10 0 0 1 5.00,6.86L5.00,5.64A4.10 4.10 0 0 1 9.10,1.55z","M13.07,5.82C12.95,7.25,11.05,7.25,10.93,5.82C10.97,5.35,11.60,5.35,11.65,5.82C11.61,6.30,12.39,6.30,12.35,5.82C12.40,5.35,13.03,5.35,13.07,5.82zM15.57,5.11C15.96,5.11,16.28,5.43,16.28,5.82C16.28,6.22,15.96,6.54,15.57,6.54C15.17,6.54,14.85,6.22,14.85,5.82C14.85,5.43,15.17,5.11,15.57,5.11zM8.43,5.11C8.83,5.11,9.15,5.43,9.15,5.82C9.15,6.22,8.83,6.54,8.43,6.54C8.04,6.54,7.72,6.22,7.72,5.82C7.72,5.43,8.04,5.11,8.43,5.11z","M15.50,0.50C13.50,0.33,11.00,0.33,8.50,0.50C8.00,-0.21,6.00,-0.21,5.63,1.00C5.17,2.50,4.33,2.33,4.00,4.33C3.75,5.50,3.75,7.50,4.00,8.43C4.25,9.50,5.50,12.00,8.95,12.00L15.00,12.00C18.45,12.00,19.70,9.50,19.95,8.43C20.20,7.50,20.20,5.50,19.95,4.33C19.62,2.33,18.79,2.50,18.33,1.00C18.00,-0.21,16.00,-0.21,15.50,0.50zM15.25,10.50C12.50,10.57,11.50,10.57,8.75,10.50C7.50,10.40,6.00,9.75,5.50,7.75Q5.12,6.00,5.50,4.50C6.00,3.00,7.50,2.10,8.75,2.00C11.00,1.88,13.00,1.88,15.25,2.00C16.50,2.10,18.00,3.00,18.50,4.50Q18.88,6.00,18.50,7.75C18.00,9.75,16.50,10.40,15.25,10.50z");
            case 23: return new Shape(24.0f,12.0f,1,"M6,0 C2.69,0 0,2.69 0,6 C0,9.31 2.69,12 6,12 L18,12 C21.31,12 24,9.31 24,6 C24,2.69 21.31,0 18,0 L6,0 Z","M6,0 C2.69,0 0,2.69 0,6 C0,9.31 2.69,12 6,12 L18,12 C21.31,12 24,9.31 24,6 C24,2.69 21.31,0 18,0 L6,0 Z","M19.96,5.25L21.91,5.25Q22.43,5.31,22.10,5.77L18.69,9.77C18.39,10.06,17.81,9.87,18.10,9.18L19.04,6.75L17.09,6.75Q16.57,6.69,16.90,6.23L20.31,2.23C20.61,1.94,21.19,2.13,20.90,2.82L19.96,5.25z","M6,0 C2.69,0 0,2.69 0,6 C0,9.31 2.69,12 6,12 L18,12 C21.31,12 24,9.31 24,6 C24,2.69 21.31,0 18,0 L6,0 Z");            default: return null;
        }
    }

    private static Paths paths(int style) {
        return cache.computeIfAbsent(style, id -> {
            Shape g = shape(id);
            if (g == null) throw new IllegalArgumentException("no battery shape " + id);
            Path perimeter = path(g.perimeter), fill = path(g.fill), bolt = path(g.bolt), plus = path(g.plus);
            RectF bounds = new RectF();
            fill.computeBounds(bounds, true);
            if (bounds.isEmpty()) throw new IllegalArgumentException("empty battery fill " + id);
            return new Paths(g, perimeter, fill, bolt, plus, bounds);
        });
    }

    private static Path path(String data) {
        Path p = PathParser.createPathFromPathData(data);
        if (p == null || p.isEmpty()) throw new IllegalArgumentException("Missing battery path");
        return p;
    }

    /** One UI 8: rounded pill, level fill, the number always inside in a solid contrasting colour (see drawOneUi8). */
    public static final int BATTERY_ONE_UI8 = 25;
    /** One UI 8 fill colours: charging green, power-saver orange, low (<= 15 %) red; darker shades keep white digits readable. */
    public static final int ONE_UI8_CHARGING = 0xFF1FAF50, ONE_UI8_SAVER = 0xFFF08A00, ONE_UI8_LOW = 0xFFF0342C;

    public static boolean batteryStyleValid(int style) { return style > 0 && style <= BATTERY_ONE_UI8; }

    /**
     * Draw a battery of {@code style} into {@code bounds}: level 0..100, {@code foreground} = fill colour,
     * {@code frame} = outline colour. Returns false when nothing was drawn (caller then keeps stock).
     */
    public static boolean drawBattery(int style, Canvas c, RectF bounds, int level, boolean charging, boolean saver, boolean percent, int foreground, int frame) {
        if (!batteryStyleValid(style) || level < 0 || level > 100 || bounds == null || bounds.isEmpty() || !Float.isFinite(bounds.width()) || !Float.isFinite(bounds.height())) return false;
        if (style == BATTERY_HIDDEN) return true;
        if (style == BATTERY_ONE_UI8) return drawOneUi8(c, bounds, level, charging, foreground, frame);
        int save = c.saveLayer(bounds, null);
        boolean complete = false;
        try {
            c.clipRect(bounds);
            Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
            boolean circle = style == 2 || style == 3 || style == 4 || style == 9 || style == 10;
            Paths paths = circle || style == BATTERY_TEXT || style == 24 ? null : paths(style);
            float width = circle ? 24 : style == BATTERY_TEXT ? 36 : style == 24 ? 24 : paths.geometry.width;
            float height = circle ? 24 : style == BATTERY_TEXT ? 14 : style == 24 ? 12 : paths.geometry.height;
            float scale = Math.min(bounds.width() / width, bounds.height() / height);
            if (!Float.isFinite(scale) || scale <= 0) return false;
            c.translate(bounds.centerX() - width * scale / 2, bounds.centerY() - height * scale / 2);
            c.scale(scale, scale);
            int fg = foreground, dim = (foreground & 0x00ffffff) | ((Color.alpha(foreground) * 45 / 255) << 24);
            // Labels and the bolt are drawn twice: over the filled part in the inverse of the fill, over the empty
            // part in the tint ({@code frame}) itself, so the number stays readable on light and dark status bars.
            String glyph = charging ? "ϟ" : saver ? "+" : null;
            if (style == BATTERY_TEXT) {
                text(c, p, (charging ? "ϟ " : saver ? "+ " : "") + level + "%", width / 2, height / 2, height * .82f, width, frame);
            } else if (circle) {
                float radius = style == 9 || style == 10 ? 10.1f : 9.2f, stroke = 24f / 6.5f;
                p.setColor(dim);
                if (style == 4) {
                    c.drawCircle(12, 12, radius, p);
                    p.setColor(fg);
                    c.drawCircle(12, 12, radius * level / 100f, p);
                } else {
                    p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(stroke); p.setColor(frame);
                    if (style == 3 || style == 10) p.setPathEffect(new DashPathEffect(new float[]{3, 2}, 0));
                    RectF ring = new RectF(12 - radius, 12 - radius, 12 + radius, 12 + radius);
                    c.drawArc(ring, -90, 360, false, p);
                    p.setColor(fg);
                    if (level > 0) c.drawArc(ring, -90, 360 * level / 100f, false, p);
                    p.setStyle(Paint.Style.FILL); p.setPathEffect(null);
                }
                String label = glyph != null ? glyph : percent ? Integer.toString(level) : null;
                if (label != null) {
                    float size = glyph != null ? 13 : 24 * .6f;
                    if (style == 4) {
                        Path disc = new Path(); disc.addCircle(12, 12, radius * level / 100f, Path.Direction.CW);
                        int sv = c.save();
                        try { c.clipPath(disc); text(c, p, label, 12, 12, size, 15, contrast(fg)); } finally { c.restoreToCount(sv); }
                        sv = c.save();
                        try { c.clipOutPath(disc); text(c, p, label, 12, 12, size, 15, frame); } finally { c.restoreToCount(sv); }
                    } else text(c, p, label, 12, 12, size, 15, frame);   // ring centre is always "empty"
                }
            } else if (style == 24) {
                RectF pill = new RectF(.6f, .6f, 23.4f, 11.4f);
                p.setColor(dim); c.drawRoundRect(pill, 5.4f, 5.4f, p);
                Path rounded = new Path(); rounded.addRoundRect(pill, 5.4f, 5.4f, Path.Direction.CW);
                RectF filled = new RectF(pill.left, pill.top, pill.left + pill.width() * level / 100f, pill.bottom);
                int clip = c.save();
                try { c.clipPath(rounded); c.clipRect(filled); p.setColor(fg); c.drawRect(pill, p); } finally { c.restoreToCount(clip); }
                String label = glyph != null ? glyph : percent ? Integer.toString(level) : null;
                if (label != null) split(c, p, label, 12, 6, glyph != null ? 9.5f : 12 * .66f, 19, filled, fg, frame);
            } else {
                p.setColor(dim); c.drawPath(paths.fill, p);
                p.setColor(paths.geometry.perimeter.equals(paths.geometry.fill) ? dim : frame); c.drawPath(paths.perimeter, p);
                RectF fill = paths.fillBounds;
                float fraction = level / 100f;
                RectF filled;
                switch (paths.geometry.direction) {
                    case 1: filled = new RectF(fill.left, fill.top, fill.left + fill.width() * fraction, fill.bottom); break;
                    case 2: filled = new RectF(fill.right - fill.width() * fraction, fill.top, fill.right, fill.bottom); break;
                    default: filled = new RectF(fill.left, fill.bottom - fill.height() * fraction, fill.right, fill.bottom); break;
                }
                int clip = c.save();
                try {
                    c.clipPath(paths.fill); c.clipRect(filled);
                    p.setColor(fg);
                    if (level > 0) c.drawPath(paths.fill, p);
                } finally { c.restoreToCount(clip); }
                if (glyph != null) {
                    if (!charging && paths.geometry.plus.equals(paths.geometry.perimeter)) split(c, p, "+", fill.centerX(), fill.centerY(), Math.min(9, fill.height() * .8f), fill.width() * .9f, filled, fg, frame);
                    else {
                        Path mark = charging ? paths.bolt : paths.plus;
                        int sv = c.save();
                        try { c.clipRect(filled); p.setColor(contrast(fg)); c.drawPath(mark, p); } finally { c.restoreToCount(sv); }
                        sv = c.save();
                        try { c.clipOutRect(filled.left, filled.top, filled.right, filled.bottom); p.setColor(frame); c.drawPath(mark, p); } finally { c.restoreToCount(sv); }
                    }
                } else if (percent) {
                    split(c, p, Integer.toString(level), fill.centerX(), fill.centerY(), Math.max(height * .6f, fill.height() * .72f), fill.width() * .92f, filled, fg, frame);
                }
            }
            complete = true;
            return true;
        } finally {
            try { if (!complete) c.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR); } finally { c.restoreToCount(save); }
        }
    }

    private static int contrast(int color) { return Color.luminance(color) > .45 ? Color.BLACK : Color.WHITE; }

    /**
     * One UI 8 battery. A 24 x 12 pill: the empty part is the tint at ~30 % of its alpha, the level fill is {@code fill}
     * (the tint, or green / orange / red for charging / power saving / low), and the number sits inside. Digits are
     * drawn solid: black or white against the fill where they overlap it, the tint itself over the empty part, so they
     * stay readable on light and dark bars and in the Control Center header. (Stock RedMagic punches the digits out of
     * the pill and paints them over again in a near-identical "overlay" colour, which is why its number is barely or
     * not at all visible; a knock-out would depend on what happens to be behind the icon, so it is not used here.)
     * Charging adds a small bolt after the number, both centred together.
     */
    static boolean drawOneUi8(Canvas c, RectF bounds, int level, boolean charging, int fill, int tint) {
        final float W = 24f, H = 12f;
        float scale = Math.min(bounds.width() / W, bounds.height() / H);
        if (!Float.isFinite(scale) || scale <= 0) return false;
        int save = c.save();
        try {
            c.translate(bounds.centerX() - W * scale / 2, bounds.centerY() - H * scale / 2);
            c.scale(scale, scale);
            Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
            if ((fill & 0x00ffffff) == (tint & 0x00ffffff)) fill = opaqueish(fill);   // plain level fill: as strong as the digits
            int track = withAlphaFraction(tint, .3f);
            RectF pill = new RectF(.5f, .5f, 22.6f, 11.5f);
            float r = 4.6f;
            Path body = new Path(); body.addRoundRect(pill, r, r, Path.Direction.CW);
            p.setColor(track); c.drawPath(body, p);
            RectF nub = new RectF(22.9f, 4.1f, 24f, 7.9f);
            c.drawRoundRect(nub, .55f, .55f, p);
            RectF filled = new RectF(pill.left, pill.top, pill.left + pill.width() * Math.max(0, Math.min(100, level)) / 100f, pill.bottom);
            if (level > 0) {
                int sv = c.save();
                try { c.clipPath(body); c.clipRect(filled); p.setColor(fill); c.drawRect(pill, p); } finally { c.restoreToCount(sv); }
            }
            // label: the number (+ bolt), centred in the pill, shrunk to fit
            String digits = Integer.toString(level);
            p.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
            p.setTextAlign(Paint.Align.LEFT);
            float size = 8.8f; p.setTextSize(size);
            float boltW = charging ? 4.4f : 0f, gap = charging ? .5f : 0f;
            float textW = p.measureText(digits), avail = pill.width() - 2.4f;
            if (textW + gap + boltW > avail) { float k = avail / (textW + gap + boltW); size *= k; p.setTextSize(size); textW = p.measureText(digits); boltW *= k; gap *= k; }
            float startX = pill.centerX() - (textW + gap + boltW) / 2f;
            Paint.FontMetrics m = p.getFontMetrics();
            float baseline = pill.centerY() - (m.ascent + m.descent) / 2f;
            Path bolt = null;
            if (charging) {
                float bx = startX + textW + gap, bh = size * .86f, by = pill.centerY() - bh / 2f, bw = boltW;
                bolt = new Path();
                bolt.moveTo(bx + bw * .62f, by);
                bolt.lineTo(bx + bw * .06f, by + bh * .58f);
                bolt.lineTo(bx + bw * .46f, by + bh * .58f);
                bolt.lineTo(bx + bw * .34f, by + bh);
                bolt.lineTo(bx + bw * .94f, by + bh * .40f);
                bolt.lineTo(bx + bw * .54f, by + bh * .40f);
                bolt.close();
            }
            int over = contrast(fill), under = opaqueish(tint);
            for (int pass = 0; pass < 2; pass++) {
                int sv = c.save();
                try {
                    if (pass == 0) { if (filled.width() <= 0) continue; c.clipRect(filled); p.setColor(over); }
                    else { c.clipOutRect(filled.left, filled.top, filled.right, filled.bottom); p.setColor(under); }
                    c.drawText(digits, startX, baseline, p);
                    if (bolt != null) c.drawPath(bolt, p);
                } finally { c.restoreToCount(sv); }
            }
            return true;
        } finally { c.restoreToCount(save); }
    }

    private static int withAlphaFraction(int color, float f) {
        int a = Math.round(Color.alpha(color) * f);
        return (color & 0x00ffffff) | (Math.max(0x26, Math.min(255, a)) << 24);
    }

    /** The tint with at least ~88 % alpha, so digits over the empty part are as strong as the other status icons. */
    private static int opaqueish(int color) {
        return Color.alpha(color) >= 0xE0 ? color : (color & 0x00ffffff) | 0xE0000000;
    }

    /** Black or white against {@code color}, or the tint when {@code color} is too transparent to judge (stock readability fix). */
    static int readableOn(int color, int tint) {
        return Color.alpha(color) < 0x99 ? opaqueish(tint) : contrast(color);
    }

    /** Label drawn in the inverse of the fill where it overlaps {@code filled}, in the tint everywhere else. */
    private static void split(Canvas c, Paint p, String label, float x, float y, float size, float width, RectF filled, int fill, int tint) {
        int sv = c.save();
        try { c.clipRect(filled); text(c, p, label, x, y, size, width, contrast(fill)); } finally { c.restoreToCount(sv); }
        sv = c.save();
        try { c.clipOutRect(filled.left, filled.top, filled.right, filled.bottom); text(c, p, label, x, y, size, width, tint); } finally { c.restoreToCount(sv); }
    }

    private static void text(Canvas c, Paint p, String label, float x, float y, float size, float width, int color) {
        p.setStyle(Paint.Style.FILL); p.setColor(color);
        p.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
        p.setTextAlign(Paint.Align.CENTER); p.setTextSize(size);
        float measured = p.measureText(label);
        if (measured > width && measured > 0) p.setTextSize(size * width / measured);
        Paint.FontMetrics m = p.getFontMetrics();
        c.drawText(label, x, y - (m.ascent + m.descent) / 2, p);
    }

    // ================================================================================================= outline (QS style 1, brightness style 1)

    /** Outline composition reuses stock drawing, including its state colours, tint and alpha. */
    static boolean drawOutline(Canvas canvas, GradientDrawable drawable, float density, NativeDraw stock) throws Throwable {
        if (!Float.isFinite(density) || density <= 0) return false;
        Rect bounds = drawable.getBounds(); int shape = drawable.getShape();
        if (bounds.isEmpty() || (shape != GradientDrawable.RECTANGLE && shape != GradientDrawable.OVAL)) return false;
        float stroke = Math.min(2.5f * density, Math.min(bounds.width(), bounds.height()) / 4f);
        float[] radii = drawable.getCornerRadii();
        if (radii == null) { radii = new float[8]; Arrays.fill(radii, drawable.getCornerRadius()); }
        if (radii.length != 8) return false;
        for (float r : radii) if (!Float.isFinite(r) || r < 0) return false;
        RectF outer = new RectF(bounds), inner = new RectF(bounds); inner.inset(stroke, stroke);
        Path ring = new Path(), interior = new Path(); ring.setFillType(Path.FillType.EVEN_ODD);
        if (shape == GradientDrawable.OVAL) { ring.addOval(outer, Path.Direction.CW); interior.addOval(inner, Path.Direction.CW); }
        else { ring.addRoundRect(outer, radii, Path.Direction.CW); float[] inset = radii.clone(); for (int i = 0; i < 8; i++) inset[i] = Math.max(0, inset[i] - stroke); interior.addRoundRect(inner, inset, Path.Direction.CW); }
        ring.addPath(interior);
        int saved = canvas.save();
        try {
            int layer = canvas.saveLayerAlpha(outer, 128);
            try { canvas.clipPath(interior); stock.draw(); } finally { canvas.restoreToCount(layer); }
            canvas.clipPath(ring); stock.draw();
            return true;
        } finally { canvas.restoreToCount(saved); }
    }

    // ================================================================================================= QS tile finishes

    /** Native colour/alpha is the base; SRC_ATOP confines static shading to that background. Styles 1..7. */
    static boolean drawTile(int style, Canvas canvas, GradientDrawable tile, float density, NativeDraw stock) throws Throwable {
        if (style == 1) return drawOutline(canvas, tile, density, stock);
        if (style < 2 || style > 7 || !Float.isFinite(density) || density <= 0 || tile.getBounds().isEmpty() || (tile.getShape() != GradientDrawable.RECTANGLE && tile.getShape() != GradientDrawable.OVAL)) return false;
        RectF b = new RectF(tile.getBounds()); float unit = Math.min(density, Math.min(b.width(), b.height()) / 20f);
        int saved = canvas.saveLayer(b, null); boolean complete = false;
        try {
            stock.draw();
            Paint p = new Paint(Paint.ANTI_ALIAS_FLAG); p.setBlendMode(BlendMode.SRC_ATOP);
            switch (style) {
                case 2: {
                    gradient(canvas, b, p, 0x14ffffff, 0x50000000, false);
                    Path facets = new Path();
                    facets.moveTo(b.left + 3 * unit, b.top + 7 * unit); facets.lineTo(b.centerX() - 2 * unit, b.top + 7 * unit); facets.lineTo(b.centerX() + 2 * unit, b.bottom - 3 * unit); facets.lineTo(b.left + 3 * unit, b.bottom - 3 * unit); facets.close();
                    facets.moveTo(b.centerX() + 2 * unit, b.top + 3 * unit); facets.lineTo(b.right - 3 * unit, b.top + 3 * unit); facets.lineTo(b.right - 3 * unit, b.bottom - 7 * unit); facets.lineTo(b.centerX() + 6 * unit, b.bottom - 7 * unit); facets.close();
                    p.setShader(null); p.setColor(0x30ffffff); canvas.drawPath(facets, p);
                    break;
                }
                case 3: {
                    bevel(canvas, tile, b, p, 2 * unit, 0x60ffffff, 0x60000000);
                    RectF inset = new RectF(b); inset.inset(2 * unit, 2 * unit);
                    Path ring = ring(tile, b, 2 * unit, 4 * unit);
                    p.setShader(new LinearGradient(0, inset.top, 0, inset.bottom, 0x38000000, 0x38ffffff, Shader.TileMode.CLAMP)); canvas.drawPath(ring, p);
                    break;
                }
                case 4: {
                    gradient(canvas, b, p, 0x34ffffff, 0x24000000, true);
                    Path reflection = new Path(); reflection.moveTo(b.left, b.top); reflection.lineTo(b.right, b.top); reflection.lineTo(b.right, b.top + b.height() * 0.22f); reflection.lineTo(b.left, b.top + b.height() * 0.72f); reflection.close();
                    p.setShader(new LinearGradient(b.left, b.top, b.right, b.bottom, 0x45ffffff, 0x00ffffff, Shader.TileMode.CLAMP)); canvas.drawPath(reflection, p);
                    break;
                }
                case 5: gradient(canvas, b, p, 0x60000000, 0x00000000, false); break;
                case 6: {
                    p.setColor(0x30000000); canvas.drawRect(b, p); p.setColor(0x48ffffff);
                    float inset = 4 * unit;
                    RectF capsule = new RectF(b.left + inset, b.top + inset, b.left + inset + Math.min(b.height() - 2 * inset, b.width() - 2 * inset), b.bottom - inset);
                    canvas.drawRoundRect(capsule, capsule.height() / 2, capsule.height() / 2, p);
                    break;
                }
                case 7: gradient(canvas, b, p, 0x0cffffff, 0x10000000, false); bevel(canvas, tile, b, p, 1.5f * unit, 0x65ffffff, 0x35000000); break;
                default: return false;
            }
            complete = true;
            return true;
        } finally {
            try { if (!complete) canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR); } finally { canvas.restoreToCount(saved); }
        }
    }

    private static void gradient(Canvas c, RectF b, Paint p, int first, int last, boolean diagonal) {
        p.setShader(new LinearGradient(b.left, b.top, diagonal ? b.right : b.left, b.bottom, first, last, Shader.TileMode.CLAMP)); c.drawRect(b, p); p.setShader(null);
    }
    private static void bevel(Canvas c, GradientDrawable tile, RectF b, Paint p, float width, int top, int bottom) {
        p.setShader(new LinearGradient(0, b.top, 0, b.bottom, top, bottom, Shader.TileMode.CLAMP)); c.drawPath(ring(tile, b, 0, width), p); p.setShader(null);
    }
    private static Path ring(GradientDrawable tile, RectF bounds, float start, float end) {
        Path p = outline(tile, bounds, start); p.setFillType(Path.FillType.EVEN_ODD); p.addPath(outline(tile, bounds, end)); return p;
    }
    private static Path outline(GradientDrawable tile, RectF bounds, float inset) {
        RectF box = new RectF(bounds); box.inset(inset, inset); Path p = new Path();
        if (box.isEmpty()) return p;
        if (tile.getShape() == GradientDrawable.OVAL) p.addOval(box, Path.Direction.CW);
        else {
            float[] original = tile.getCornerRadii(); float[] radii = original == null ? null : original.clone();
            if (radii == null) { radii = new float[8]; Arrays.fill(radii, tile.getCornerRadius()); }
            for (int i = 0; i < radii.length; i++) radii[i] = Math.max(0, radii[i] - inset);
            p.addRoundRect(box, radii, Path.Direction.CW);
        }
        return p;
    }

    // ================================================================================================= notification / power-menu finishes

    /** Static overlays over a native rounded background that was already drawn into the same layer. Styles 1..5. */
    static boolean drawCard(int style, Canvas canvas, RectF b, float[] corners, float density) {
        if (style < 1 || style > 5 || b.isEmpty() || corners == null || corners.length != 8 || !Float.isFinite(density) || density <= 0) return false;
        for (float r : corners) if (!Float.isFinite(r) || r < 0) return false;
        int save = canvas.saveLayer(b, null); boolean complete = false;
        try {
            canvas.clipPath(outlineRect(b, corners, 0));
            Paint p = new Paint(Paint.ANTI_ALIAS_FLAG); float u = Math.min(density, Math.min(b.width(), b.height()) / 12f);
            switch (style) {
                case 1: p.setShader(new LinearGradient(b.left, b.top, b.right, b.bottom, 0x90ffffff, 0x60000000, Shader.TileMode.CLAMP)); canvas.drawRect(b.left, b.top, b.right, b.top + 3 * u, p); canvas.drawRect(b.left, b.bottom - 3 * u, b.right, b.bottom, p); break;
                case 2: {
                    p.setColor(0x45000000); canvas.drawRect(b, p); p.setColor(0x28ffffff);
                    Path facets = new Path();
                    facets.moveTo(b.left, b.top + 6 * u); facets.lineTo(b.centerX() - 3 * u, b.top + 6 * u); facets.lineTo(b.centerX() + 3 * u, b.bottom - 3 * u); facets.lineTo(b.left, b.bottom - 3 * u); facets.close();
                    facets.moveTo(b.centerX() + 3 * u, b.top + 3 * u); facets.lineTo(b.right, b.top + 3 * u); facets.lineTo(b.right, b.bottom - 6 * u); facets.lineTo(b.centerX() + 9 * u, b.bottom - 6 * u); facets.close();
                    canvas.drawPath(facets, p);
                    break;
                }
                case 3: p.setShader(new LinearGradient(b.left, b.top, b.right, b.bottom, 0x20ffffff, 0x08000000, Shader.TileMode.CLAMP)); canvas.drawRect(b, p); break;
                case 4: p.setColor(0x50000000); canvas.drawRect(b.left, b.bottom - 12 * u, b.right, b.bottom, p); p.setColor(0x28ffffff); canvas.drawRect(b.left + 12 * u, b.bottom - 8 * u, b.right - 12 * u, b.bottom - 4 * u, p); p.setColor(0x40ffffff); canvas.drawRect(b.left + 24 * u, b.bottom - 4 * u, b.right - 24 * u, b.bottom, p); break;
                case 5: {
                    p.setShader(new LinearGradient(b.left, b.top, b.right, b.bottom, 0x65ffffff, 0x60000000, Shader.TileMode.CLAMP));
                    Path ring = outlineRect(b, corners, 0); ring.setFillType(Path.FillType.EVEN_ODD); ring.addPath(outlineRect(b, corners, 4 * u)); canvas.drawPath(ring, p);
                    break;
                }
                default: return false;
            }
            complete = true;
            return true;
        } finally {
            try { if (!complete) canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR); } finally { canvas.restoreToCount(save); }
        }
    }

    private static Path outlineRect(RectF bounds, float[] corners, float inset) {
        RectF b = new RectF(bounds); b.inset(inset, inset); Path p = new Path();
        if (b.isEmpty()) return p;
        float[] radii = corners.clone(); for (int i = 0; i < 8; i++) radii[i] = Math.max(0, radii[i] - inset);
        p.addRoundRect(b, radii, Path.Direction.CW); return p;
    }
    private static Path ringRect(RectF bounds, float[] corners, float start, float end) {
        Path path = outlineRect(bounds, corners, start); path.setFillType(Path.FillType.EVEN_ODD); path.addPath(outlineRect(bounds, corners, end)); return path;
    }

    // ================================================================================================= brightness

    /** Outline, Neumorph and Thin treatments of one GradientDrawable layer of the slider. Styles 1..3. */
    static boolean drawBrightness(int style, Canvas canvas, GradientDrawable drawable, float density, NativeDraw nativeDraw) throws Throwable {
        if (style == 1) {
            if (drawable.getBounds().isEmpty()) return false;
            int saved = canvas.saveLayer(new RectF(drawable.getBounds()), null); boolean complete = false;
            try { if (!drawOutline(canvas, drawable, density, nativeDraw)) return false; complete = true; return true; }
            finally { try { if (!complete) canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR); } finally { canvas.restoreToCount(saved); } }
        }
        if (style == 2) return drawTile(3, canvas, drawable, density, nativeDraw);
        if (style != 3 || !Float.isFinite(density) || density <= 0 || drawable.getShape() != GradientDrawable.RECTANGLE || drawable.getBounds().isEmpty()) return false;
        RectF bounds = new RectF(drawable.getBounds()); float height = Math.min(bounds.height(), 4 * density);
        RectF line = new RectF(bounds.left, bounds.centerY() - height / 2, bounds.right, bounds.centerY() + height / 2);
        int layer = canvas.saveLayer(bounds, null); boolean complete = false;
        try {
            Path clip = new Path(); clip.addRoundRect(line, height / 2, height / 2, Path.Direction.CW); canvas.clipPath(clip);
            nativeDraw.draw(); complete = true; return true;
        } finally { try { if (!complete) canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR); } finally { canvas.restoreToCount(layer); } }
    }

    // ================================================================================================= volume

    /** Rising-inspired shading over one native vendor slider draw. Styles 1..6. */
    static boolean drawVolume(int style, Canvas canvas, RectF bounds, float[] corners, float density, NativeDraw stock) throws Throwable {
        if (style < 1 || style > 6 || bounds.isEmpty() || !Float.isFinite(density) || density <= 0 || corners == null || corners.length != 8) return false;
        for (float n : corners) if (!Float.isFinite(n) || n < 0) return false;
        int save = canvas.saveLayer(null, null); boolean complete = false;
        try {
            stock.draw();
            Paint p = new Paint(Paint.ANTI_ALIAS_FLAG); p.setBlendMode(BlendMode.SRC_ATOP); float unit = Math.min(density, Math.min(bounds.width(), bounds.height()) / 12f);
            if (style == 2) { p.setShader(new LinearGradient(bounds.left, bounds.top, bounds.right, bounds.bottom, 0x50d3cbef, 0x60353778, Shader.TileMode.CLAMP)); canvas.drawRect(bounds, p); }
            if (style == 3 || style == 4 || style == 5) {
                p.setShader(new LinearGradient(bounds.left, bounds.top, bounds.left, bounds.bottom, 0x70ffffff, 0x70000000, Shader.TileMode.CLAMP)); canvas.drawPath(ringRect(bounds, corners, 0, 2 * unit), p);
                p.setShader(new LinearGradient(bounds.left, bounds.top, bounds.left, bounds.bottom, 0x50000000, 0x50ffffff, Shader.TileMode.CLAMP)); canvas.drawPath(ringRect(bounds, corners, 2 * unit, 4 * unit), p);
                if (style == 5) { p.setShader(new LinearGradient(bounds.left, bounds.top, bounds.right, bounds.bottom, 0x45ffffff, 0x55000000, Shader.TileMode.CLAMP)); canvas.drawPath(ringRect(bounds, corners, 5 * unit, 7 * unit), p); }
            }
            if (style == 6) { p.setShader(new LinearGradient(bounds.left, bounds.top, bounds.right, bounds.bottom, 0x70000000, 0x10ffffff, Shader.TileMode.CLAMP)); canvas.drawRect(bounds, p); }
            if (style == 1 || style == 4) { p.setShader(null); p.setBlendMode(BlendMode.DST_OUT); p.setColor(0x80000000); canvas.drawPath(outlineRect(bounds, corners, (style == 4 ? 4 : 2.5f) * unit), p); }
            complete = true;
            return true;
        } finally { try { if (!complete) canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR); } finally { canvas.restoreToCount(save); } }
    }
}
