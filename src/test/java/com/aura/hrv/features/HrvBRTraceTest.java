package com.aura.hrv.features;

import com.aura.hrv.util.MathUtils;
import org.apache.commons.math3.analysis.interpolation.SplineInterpolator;
import org.apache.commons.math3.analysis.polynomials.PolynomialSplineFunction;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

/**
 * 逐步追踪 HrvBreathingRate 内部计算过程，便于验证算法正确性。
 */
public class HrvBRTraceTest {

    @Test
    public void traceBreathingRateCalculation() {
        // 原始 RR 数据
        List<Double> rr = Arrays.asList(
            382.0,277.0,880.0,733.0,513.0,328.0,468.0,573.0,387.0,322.0,
            726.0,478.0,411.0,677.0,508.0,345.0,717.0,451.0,449.0,338.0,
            348.0,453.0,375.0,587.0,588.0,617.0,496.0,473.0,589.0,578.0,
            614.0,301.0,555.0,519.0,305.0,350.0,536.0,448.0,610.0,544.0,
            358.0,73.0
        );

        System.out.println("=== HrvBreathingRate 逐步追踪 ===");
        System.out.println("输入 RR 长度: " + rr.size());

        // Step 1: 预处理 — 通过 HrvPreprocessing.getNnIntervals 获得 NN
        // （从测试上下文复用已知 NN 结果：与完整分析一致）
        // 这里直接对 RR 调用 getBreathingRate 并观察最终结果
        // 同时手动执行内部步骤以打印中间值

        // Step 1: 过滤 NaN
        List<Double> cleanNn = new java.util.ArrayList<>();
        for (Double v : rr) {
            if (v != null && !Double.isNaN(v)) cleanNn.add(v);
        }
        System.out.println("\nStep 1 - NaN过滤后长度: " + cleanNn.size());
        System.out.println("         前5值: " + cleanNn.subList(0, Math.min(5, cleanNn.size())));

        // Step 2: 构造累积时间轴（秒），强制从 0 开始
        double[] timestamps = new double[cleanNn.size()];
        double cumsum = 0.0;
        for (int i = 0; i < cleanNn.size(); i++) {
            cumsum += cleanNn.get(i);
            timestamps[i] = cumsum / 1000.0;
        }
        double first = timestamps[0];
        for (int i = 0; i < timestamps.length; i++) timestamps[i] -= first;

        System.out.println("\nStep 2 - 时间轴（秒），前5: " + Arrays.toString(Arrays.copyOf(timestamps, 5)));
        System.out.println("         最后时间点: " + timestamps[timestamps.length - 1] + " s");

        // Step 3: 均匀插值时间网格
        int fs = 4;
        double endTime = timestamps[timestamps.length - 1];
        double step = 1.0 / fs;
        int n = (int) Math.ceil(endTime / step);
        double[] interpTimestamps = new double[n];
        for (int i = 0; i < n; i++) interpTimestamps[i] = i * step;

        System.out.println("\nStep 3 - 插值网格点数: " + n + "  (endTime=" + endTime + ", step=" + step + ")");
        System.out.println("         前5: " + Arrays.toString(Arrays.copyOf(interpTimestamps, Math.min(5, n))));
        System.out.println("         末尾: " + interpTimestamps[n - 1]);

        // Step 4: 三次样条插值
        double[] nnArray = MathUtils.toArray(cleanNn);
        SplineInterpolator interpolator = new SplineInterpolator();
        PolynomialSplineFunction spline = interpolator.interpolate(timestamps, nnArray);
        double xMin = timestamps[0], xMax = timestamps[timestamps.length - 1];
        double[] rr_interp = new double[n];
        for (int i = 0; i < n; i++) {
            double t = interpTimestamps[i];
            if (t < xMin) t = xMin;
            if (t > xMax) t = xMax;
            rr_interp[i] = spline.value(t);
        }
        System.out.println("\nStep 4 - 三次样条插值结果，前5: " + Arrays.toString(Arrays.copyOf(rr_interp, Math.min(5, n))));

        // Step 5: 去 DC
        double mean = MathUtils.mean(rr_interp);
        double[] rr_normalized = new double[n];
        for (int i = 0; i < n; i++) rr_normalized[i] = rr_interp[i] - mean;
        System.out.println("\nStep 5 - 去DC: mean=" + mean);
        System.out.println("         normalized前5: " + Arrays.toString(Arrays.copyOf(rr_normalized, Math.min(5, n))));

        // Step 6: Welch PSD（复用包内方法）
        int nfft = 4096;
        double[][] freqPsd = HrvFrequencyDomainFeatures.welchPsd(rr_normalized, fs, nfft);
        double[] freqs = freqPsd[0];
        double[] psd   = freqPsd[1];
        System.out.println("\nStep 6 - Welch PSD: 频率点数=" + freqs.length);
        System.out.println("         频率分辨率=" + (freqs.length > 1 ? freqs[1] - freqs[0] : "N/A") + " Hz");

        // 打印呼吸频段内的前10个频率-PSD对
        System.out.println("         呼吸频段 [0.1, 0.4] Hz 内的频率-PSD:");
        int printed = 0;
        double maxPsd = Double.NEGATIVE_INFINITY;
        double breathingRateHz = Double.NaN;
        for (int i = 0; i < freqs.length; i++) {
            if (freqs[i] >= 0.1 && freqs[i] <= 0.4) {
                if (printed < 15) {
                    System.out.printf("           freqs[%d]=%.6f Hz, psd=%.6e%n", i, freqs[i], psd[i]);
                    printed++;
                }
                if (psd[i] > maxPsd) {
                    maxPsd = psd[i];
                    breathingRateHz = freqs[i];
                }
            }
        }

        // Step 7: 主峰
        System.out.println("\nStep 7 - 主峰频率: " + breathingRateHz + " Hz (PSD=" + maxPsd + ")");

        // Step 8: 换算
        double breathingRatePerMinute = breathingRateHz * 60.0;
        System.out.println("\nStep 8 - 呼吸频率: " + breathingRateHz + " Hz = " + breathingRatePerMinute + " 次/分钟");

        // 验证与 HrvBreathingRate.getBreathingRate 结果一致
        java.util.Map<String, Double> brResult = HrvBreathingRate.getBreathingRate(rr);
        System.out.println("\n=== 与 HrvBreathingRate.getBreathingRate(rr) 对比 ===");
        System.out.println("API breathing_rate_hz         = " + brResult.get("breathing_rate_hz"));
        System.out.println("API breathing_rate_per_minute = " + brResult.get("breathing_rate_per_minute"));
        System.out.println("手动计算 breathing_rate_hz    = " + breathingRateHz);
        System.out.println("结果一致: " + (brResult.get("breathing_rate_hz").equals(breathingRateHz)));
    }
}
