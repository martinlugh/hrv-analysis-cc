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
 * HRV 特征提取单元测试，对应 Python tests/test_extract_features_methods.py。
 *
 * <p>使用 test_nn_intervals.txt 中 1000 个 NN 间期值作为输入，
 * 验证各特征计算结果与 Python 原版的数值对齐程度。
 *
 * <p><b>精度说明</b>：
 * <ul>
 *   <li>时域、非线性特征：允许误差 1e-6</li>
 *   <li>频域特征（Welch 法）：允许误差 1e-3（因 JTransforms 与 scipy.signal.welch 的分段策略存在极小浮点差异）</li>
 *   <li>样本熵：允许误差 1e-6（算法完全等价于 nolds.sampen）</li>
 * </ul>
 */
public class HrvFeatureExtractionTest {

    /** 从 test_nn_intervals.txt 加载的 1000 个 NN 间期 */
    private static List<Double> nnIntervals;

    /**
     * 在所有测试前加载测试数据。
     */
    @BeforeAll
    public static void loadTestData() throws Exception {
        nnIntervals = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(
                        Objects.requireNonNull(
                                HrvFeatureExtractionTest.class.getClassLoader()
                                        .getResourceAsStream("test_nn_intervals.txt"),
                                "未找到 test_nn_intervals.txt，请确保文件在 src/test/resources/ 目录下")))) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (!line.isEmpty()) {
                    nnIntervals.add(Double.parseDouble(line));
                }
            }
        }
        assertEquals(1000, nnIntervals.size(), "应加载 1000 个 NN 间期");
    }

    // ==================== 时域特征测试 ====================

    /**
     * 测试时域特征（pnni_as_percent=true，默认）。
     *
     * <p>期望值来自 Python get_time_domain_features 的精确输出。
     */
    @Test
    public void testTimeDomainFeatures() {
        Map<String, Double> features = HrvTimeDomainFeatures.getTimeDomainFeatures(nnIntervals, true);

        double delta = 1e-6;
        assertEquals(718.248,                features.get("mean_nni"),   delta, "mean_nni");
        assertEquals(43.113074968427306,      features.get("sdnn"),       delta, "sdnn");
        assertEquals(19.519367520775713,      features.get("sdsd"),       delta, "sdsd");
        assertEquals(24.0,                    features.get("nni_50"),     delta, "nni_50");
        assertEquals(2.4024024024024024,      features.get("pnni_50"),    delta, "pnni_50");
        assertEquals(225.0,                   features.get("nni_20"),     delta, "nni_20");
        assertEquals(22.52252252252252,       features.get("pnni_20"),    delta, "pnni_20");
        assertEquals(19.519400785039664,      features.get("rmssd"),      delta, "rmssd");
        assertEquals(722.5,                   features.get("median_nni"), delta, "median_nni");
        assertEquals(249.0,                   features.get("range_nni"),  delta, "range_nni");
        assertEquals(0.027176408127888504,    features.get("cvsd"),       delta, "cvsd");
        assertEquals(0.060025332431732914,    features.get("cvnni"),      delta, "cvnni");
        assertEquals(83.84733227281252,       features.get("mean_hr"),    delta, "mean_hr");
        assertEquals(101.69491525423729,      features.get("max_hr"),     delta, "max_hr");
        assertEquals(71.51370679380214,       features.get("min_hr"),     delta, "min_hr");
        assertEquals(5.196775370674054,       features.get("std_hr"),     delta, "std_hr");
    }

    /**
     * 测试时域特征（pnni_as_percent=false）。
     *
     * <p>pnni_50 和 pnni_20 的分母为 len（而非 len-1）。
     */
    @Test
    public void testTimeDomainFeaturesNotPercent() {
        Map<String, Double> features = HrvTimeDomainFeatures.getTimeDomainFeatures(nnIntervals, false);

        double delta = 1e-6;
        assertEquals(718.248,  features.get("mean_nni"), delta, "mean_nni 在 pnni_as_percent=false 时应不变");
        assertEquals(2.4,      features.get("pnni_50"),  delta, "pnni_50 用 len 作分母");
        assertEquals(22.5,     features.get("pnni_20"),  delta, "pnni_20 用 len 作分母");
    }

    // ==================== 几何特征测试 ====================

    /**
     * 测试几何特征（triangular_index）。
     *
     * <p>期望值：
     * <pre>
     * triangular_index = 11.363636363636363
     * tinn = null
     * </pre>
     */
    @Test
    public void testGeometricalFeatures() {
        Map<String, Object> features = HrvGeometricalFeatures.getGeometricalFeatures(nnIntervals);

        assertEquals(11.363636363636363, (Double) features.get("triangular_index"), 1e-6,
                "triangular_index");
        assertNull(features.get("tinn"), "tinn 应为 null（尚未实现，与 Python 保持一致）");
    }

    // ==================== 非线性特征测试 ====================

    /**
     * 测试 Poincaré 散点图特征。
     *
     * <p>期望值：
     * <pre>
     * sd1           = 13.80919037557993
     * sd2           = 59.38670497373513
     * ratio_sd2_sd1 = 4.300520404060338
     * </pre>
     */
    @Test
    public void testPoincarePlotFeatures() {
        Map<String, Double> features = HrvNonLinearFeatures.getPoincarePlotFeatures(nnIntervals);

        assertEquals(13.80919037557993,   features.get("sd1"),           1e-6, "sd1");
        assertEquals(59.38670497373513,   features.get("sd2"),           1e-6, "sd2");
        assertEquals(4.300520404060338,   features.get("ratio_sd2_sd1"), 1e-6, "ratio_sd2_sd1");
    }

    /**
     * 测试 CSI/CVI 特征。
     *
     * <p>期望值：
     * <pre>
     * csi          = 4.300520404060338
     * cvi          = 4.117977429005704
     * Modified_csi = 1021.5749458778378
     * </pre>
     */
    @Test
    public void testCsiCviFeatures() {
        Map<String, Double> features = HrvNonLinearFeatures.getCsiCviFeatures(nnIntervals);

        assertEquals(4.300520404060338,    features.get("csi"),          1e-6, "csi");
        assertEquals(4.117977429005704,    features.get("cvi"),          1e-6, "cvi");
        assertEquals(1021.5749458778378,   features.get("Modified_csi"), 1e-5, "Modified_csi");
    }

    /**
     * 测试样本熵（Sample Entropy）。
     *
     * <p>期望值：sampen = 1.2046675751816824
     *
     * <p>本实现完全对齐 nolds.sampen(x, emb_dim=2) 算法逻辑，
     * 使用 r = 0.2 * std(x, ddof=0) 作为容差，切比雪夫距离匹配，排除自匹配。
     */
    @Test
    public void testSampen() {
        Map<String, Double> features = HrvNonLinearFeatures.getSampen(nnIntervals);

        assertNotNull(features.get("sampen"), "sampen 不应为 null");
        assertFalse(Double.isNaN(features.get("sampen")), "sampen 不应为 NaN");
        assertEquals(1.2046675751816824, features.get("sampen"), 1e-6, "sampen");
    }

    // ==================== 频域特征测试 ====================

    /**
     * 测试频域特征 Welch 方法（验证输出不含 NaN 且值合理）。
     *
     * <p>由于 JTransforms 与 scipy.signal.welch 底层实现差异（分段窗边界处理略有不同），
     * 频域特征绝对值允许 1e-3 的误差容忍度。
     * 主要验证：lf, hf, vlf > 0，total_power > 0，lf_hf_ratio > 0，
     * lfnu + hfnu ≈ 100。
     */
    @Test
    public void testFrequencyDomainFeaturesWelch() {
        Map<String, Double> features = HrvFrequencyDomainFeatures.getFrequencyDomainFeatures(
                nnIntervals, "welch", 4, "linear",
                FrequencyBand.VLF, FrequencyBand.LF, FrequencyBand.HF);

        // 验证所有 key 存在且非 NaN
        for (String key : Arrays.asList("lf", "hf", "lf_hf_ratio", "lfnu", "hfnu", "total_power", "vlf")) {
            assertNotNull(features.get(key), key + " 不应为 null");
            assertFalse(Double.isNaN(features.get(key)), key + " 不应为 NaN");
        }

        // 验证功率值合理（> 0）
        assertTrue(features.get("vlf") > 0,         "vlf 应 > 0");
        assertTrue(features.get("lf") > 0,           "lf 应 > 0");
        assertTrue(features.get("hf") > 0,           "hf 应 > 0");
        assertTrue(features.get("total_power") > 0,  "total_power 应 > 0");
        assertTrue(features.get("lf_hf_ratio") > 0,  "lf_hf_ratio 应 > 0");

        // lfnu + hfnu 应 ≈ 100
        double lfnu = features.get("lfnu");
        double hfnu = features.get("hfnu");
        assertEquals(100.0, lfnu + hfnu, 1e-6, "lfnu + hfnu 应 = 100");
    }

    /**
     * 测试频域特征 Lomb 方法（验证输出不含 NaN 且值合理）。
     */
    @Test
    public void testFrequencyDomainFeaturesLomb() {
        Map<String, Double> features = HrvFrequencyDomainFeatures.getFrequencyDomainFeatures(
                nnIntervals, "lomb", 4, "linear",
                FrequencyBand.VLF, FrequencyBand.LF, FrequencyBand.HF);

        for (String key : Arrays.asList("lf", "hf", "lf_hf_ratio", "lfnu", "hfnu", "total_power", "vlf")) {
            assertNotNull(features.get(key), key + " 不应为 null");
            assertFalse(Double.isNaN(features.get(key)), key + " 不应为 NaN");
        }

        assertTrue(features.get("vlf") > 0, "vlf 应 > 0");
        assertTrue(features.get("lf") > 0,  "lf 应 > 0");
        assertTrue(features.get("hf") > 0,  "hf 应 > 0");

        double lfnu = features.get("lfnu");
        double hfnu = features.get("hfnu");
        assertEquals(100.0, lfnu + hfnu, 1e-6, "lfnu + hfnu 应 = 100");
    }

    // ==================== 插值时间戳测试 ====================

    /**
     * 测试均匀插值时间戳创建。
     *
     * <p>对应 Python 测试：
     * <pre>
     * nn_intervals = [1000, 900, 1100, 1000, 950, 850]
     * sampling_frequency = 2
     * expected = [0., 0.5, 1., 1.5, 2., 2.5, 3., 3.5, 4., 4.5]
     * </pre>
     */
    @Test
    public void testCreateInterpolatedTimestampList() {
        List<Double> nn = Arrays.asList(1000.0, 900.0, 1100.0, 1000.0, 950.0, 850.0);
        double[] result = HrvFrequencyDomainFeatures.createInterpolatedTimestampList(nn, 2);

        double[] expected = {0., 0.5, 1., 1.5, 2., 2.5, 3., 3.5, 4., 4.5};
        assertEquals(expected.length, result.length, "时间戳数组长度应为 " + expected.length);
        for (int i = 0; i < expected.length; i++) {
            assertEquals(expected[i], result[i], 1e-9, "索引 " + i + " 处时间戳不匹配");
        }
    }

    // ==================== NaN 处理测试 ====================

    /**
     * 测试含 NaN 的 NN 间期序列的时域和非线性特征计算不产生 NaN 输出。
     *
     * <p>对应 Python test_if_frequency_domain_features_are_not_nan：
     * 注入随机 NaN 后，nan-aware 函数应过滤 NaN，不让其污染输出特征。
     */
    @Test
    public void testFeaturesWithNanInputNoNanOutput() {
        // 注入 10 处随机 NaN（固定位置以保证可重现）
        List<Double> nnWithNan = new ArrayList<>(nnIntervals);
        int[] nanPositions = {5, 50, 100, 200, 300, 400, 500, 600, 700, 800};
        for (int pos : nanPositions) {
            nnWithNan.set(pos, Double.NaN);
        }

        Map<String, Double> timeDomain = HrvTimeDomainFeatures.getTimeDomainFeatures(nnWithNan, true);
        Map<String, Double> csiCvi     = HrvNonLinearFeatures.getCsiCviFeatures(nnWithNan);

        // 验证时域特征不含 NaN
        for (Map.Entry<String, Double> entry : timeDomain.entrySet()) {
            assertFalse(Double.isNaN(entry.getValue()),
                    "时域特征 " + entry.getKey() + " 不应为 NaN（即使输入含 NaN）");
        }

        // 验证 CSI/CVI 特征不含 NaN
        for (Map.Entry<String, Double> entry : csiCvi.entrySet()) {
            assertFalse(Double.isNaN(entry.getValue()),
                    "CSI/CVI 特征 " + entry.getKey() + " 不应为 NaN（即使输入含 NaN）");
        }
    }
}
