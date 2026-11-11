package com.themainthread.filterlab;

import java.util.List;

public record GaussianTheory(double radius, double sigma, int taps, List<Double> weights) {
}
