package com.aura.hrv;

import com.aura.hrv.features.*;
import com.aura.hrv.model.FrequencyBand;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * HrvFullAnalysis 门面类测试。
 *
 * <p><b>核心目标</b>：验证 {@link HrvFullAnalysis#analyze} 的每一项输出
 * 与各子模块单独调用的结果<b>完全一致</b>（bit-exact，误差 = 0）。
 * 确保门面类不引入任何算法变化，仅做聚合。
 */
public class HrvFullAnalysisTest {

    private static List<Double> nnIntervals;

    @BeforeAll
    public static void loadTestData() throws Exception {
        nnIntervals = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(
                        Objects.requireNonNull(
                                HrvFullAnalysisTest.class.getClassLoader()
                                        .getResourceAsStream("test_nn_intervals.txt"))))) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (!line.isEmpty()) nnIntervals.add(Double.parseDouble(line));
            }
        }
        assertEquals(1000, nnIntervals.size());
    }

    /**
     * 验证 analyze() 返回的所有 key 与各子模块单独调用结果完全一致（误差为 0）。
     */
    @Test
    public void testFullAnalysisMatchesIndividualModules() {
        // 门面调用（一次性）
        Map<String, Object> full = HrvFullAnalysis.analyze(nnIntervals);

        // 各子模块单独调用（作为基准）
        Map<String, Double> td       = HrvTimeDomainFeatures.getTimeDomainFeatures(nnIntervals, true);
        Map<String, Object> geo      = HrvGeometricalFeatures.getGeometricalFeatures(nnIntervals);
        Map<String, Double> freq     = HrvFrequencyDomainFeatures.getFrequencyDomainFeatures(nnIntervals);
        Map<String, Double> poincare = HrvNonLinearFeatures.getPoincarePlotFeatures(nnIntervals);
        Map<String, Double> csiCvi   = HrvNonLinearFeatures.getCsiCviFeatures(nnIntervals);
        Map<String, Double> sampen   = HrvNonLinearFeatures.getSampen(nnIntervals);
        Map<String, Double> br       = HrvBreathingRate.getBreathingRate(nnIntervals);

        // ---- 时域特征：逐 key 比对（bit-exact）----
        for (String key : td.keySet()) {
            assertTrue(full.containsKey(key), "门面结果缺少 key: " + key);
            assertEquals(td.get(key), (Double) full.get(key), 0.0,
                    "时域特征 [" + key + "] 不一致");
        }

        // ---- 几何特征 ----
        assertEquals((Double) geo.get("triangular_index"),
                (Double) full.get("triangular_index"), 0.0, "triangular_index 不一致");
        assertNull(full.get("tinn"), "tinn 应为 null");

        // ---- 频域特征 ----
        for (String key : freq.keySet()) {
            assertTrue(full.containsKey(key), "门面结果缺少 key: " + key);
            assertEquals(freq.get(key), (Double) full.get(key), 0.0,
                    "频域特征 [" + key + "] 不一致");
        }

        // ---- Poincaré 特征 ----
        for (String key : poincare.keySet()) {
            assertTrue(full.containsKey(key), "门面结果缺少 key: " + key);
            assertEquals(poincare.get(key), (Double) full.get(key), 0.0,
                    "Poincaré 特征 [" + key + "] 不一致");
        }

        // ---- CSI/CVI 特征 ----
        for (String key : csiCvi.keySet()) {
            assertTrue(full.containsKey(key), "门面结果缺少 key: " + key);
            assertEquals(csiCvi.get(key), (Double) full.get(key), 0.0,
                    "CSI/CVI 特征 [" + key + "] 不一致");
        }

        // ---- 样本熵 ----
        assertEquals(sampen.get("sampen"), (Double) full.get("sampen"), 0.0,
                "sampen 不一致");

        // ---- 呼吸频率（新增，自动包含）----
        assertTrue(full.containsKey("breathing_rate_hz"),
                "门面结果应包含 breathing_rate_hz");
        assertTrue(full.containsKey("breathing_rate_per_minute"),
                "门面结果应包含 breathing_rate_per_minute");
        assertEquals(br.get("breathing_rate_hz"),
                (Double) full.get("breathing_rate_hz"), 0.0,
                "breathing_rate_hz 不一致");
        assertEquals(br.get("breathing_rate_per_minute"),
                (Double) full.get("breathing_rate_per_minute"), 0.0,
                "breathing_rate_per_minute 不一致");
    }

    /**
     * 验证 analyze() 返回的 Map 包含全部预期 key（共 27 个）。
     */
    @Test
    public void testFullAnalysisContainsAllKeys() {
        Map<String, Object> full = HrvFullAnalysis.analyze(nnIntervals);

        List<String> expectedKeys = Arrays.asList(
                // 时域（16）
                "mean_nni", "sdnn", "sdsd", "nni_50", "pnni_50",
                "nni_20", "pnni_20", "rmssd", "median_nni", "range_nni",
                "cvsd", "cvnni", "mean_hr", "max_hr", "min_hr", "std_hr",
                // 几何（2）
                "triangular_index", "tinn",
                // 频域（7）
                "lf", "hf", "lf_hf_ratio", "lfnu", "hfnu", "total_power", "vlf",
                // 非线性（6）
                "sd1", "sd2", "ratio_sd2_sd1", "csi", "cvi", "Modified_csi",
                // 样本熵（1）
                "sampen",
                // 呼吸频率（2）
                "breathing_rate_hz", "breathing_rate_per_minute"
        );

        for (String key : expectedKeys) {
            assertTrue(full.containsKey(key), "门面结果缺少 key: " + key);
        }

        // 总数校验：34 个 key
        assertEquals(expectedKeys.size(), full.size(),
                "门面结果 key 数量应为 " + expectedKeys.size() + "，实际为 " + full.size());
    }

    /**
     * 验证原有各子模块的测试期望值通过门面类仍然成立。
     * 确保 HrvFullAnalysis 不改变任何已有算法计算结果。
     */
    @Test
    public void testFullAnalysisPreservesKnownValues() {
        Map<String, Object> full = HrvFullAnalysis.analyze(nnIntervals);

        double delta = 1e-6;
        // 已知时域期望值
        assertEquals(718.248,             (Double) full.get("mean_nni"),   delta);
        assertEquals(43.113074968427306,  (Double) full.get("sdnn"),       delta);
        assertEquals(19.519400785039664,  (Double) full.get("rmssd"),      delta);
        assertEquals(2.4024024024024024,  (Double) full.get("pnni_50"),    delta);

        // 几何
        assertEquals(11.363636363636363, (Double) full.get("triangular_index"), delta);

        // Poincaré
        assertEquals(13.80919037557993,  (Double) full.get("sd1"),           delta);
        assertEquals(59.38670497373513,  (Double) full.get("sd2"),           delta);
        assertEquals(4.300520404060338,  (Double) full.get("ratio_sd2_sd1"), delta);

        // CSI/CVI
        assertEquals(4.300520404060338,  (Double) full.get("csi"),          delta);
        assertEquals(4.117977429005704,  (Double) full.get("cvi"),          delta);
        assertEquals(1021.5749458778378, (Double) full.get("Modified_csi"), 1e-5);

        // 样本熵
        assertEquals(1.2046675751816824, (Double) full.get("sampen"), delta);

        // 呼吸频率：非 NaN 且在合理范围内
        double brHz = (Double) full.get("breathing_rate_hz");
        assertFalse(Double.isNaN(brHz), "breathing_rate_hz 不应为 NaN");
        assertTrue(brHz >= 0.1 && brHz <= 0.4,
                "breathing_rate_hz 应在 [0.1, 0.4] Hz 范围内，实际=" + brHz);
        assertEquals(brHz * 60.0, (Double) full.get("breathing_rate_per_minute"), 1e-9);
    }
}
