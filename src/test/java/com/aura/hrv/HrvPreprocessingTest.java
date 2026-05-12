package com.aura.hrv;

import com.aura.hrv.preprocessing.HrvPreprocessing;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * HRV 预处理模块单元测试，对应 Python tests/test_preprocessing_methods.py。
 *
 * <p>测试用例严格对齐 Python 原版测试，验证以下功能：
 * <ul>
 *   <li>remove_outliers：高低阈值过滤</li>
 *   <li>remove_ectopic_beats：malik / kamath / karlsson / acar 规则</li>
 *   <li>get_nn_intervals：完整预处理流水线</li>
 * </ul>
 */
public class HrvPreprocessingTest {

    /**
     * 测试 remove_outliers：超出 [300, 2000] 范围的值替换为 NaN。
     *
     * <p>Python 原版测试：
     * <pre>
     * rri_list = [700, 600, 2300, 200, 1000, 230, 1200]
     * expected  = [700, 600, NaN, NaN, 1000, NaN, 1200]
     * </pre>
     */
    @Test
    public void testHighLowOutlier() {
        List<Double> input = Arrays.asList(700.0, 600.0, 2300.0, 200.0, 1000.0, 230.0, 1200.0);
        List<Double> result = HrvPreprocessing.removeOutliers(input, false, 300, 2000);

        assertEquals(7, result.size());
        assertEquals(700.0, result.get(0));
        assertEquals(600.0, result.get(1));
        assertTrue(Double.isNaN(result.get(2)), "2300 应被替换为 NaN");
        assertTrue(Double.isNaN(result.get(3)), "200 应被替换为 NaN");
        assertEquals(1000.0, result.get(4));
        assertTrue(Double.isNaN(result.get(5)), "230 应被替换为 NaN");
        assertEquals(1200.0, result.get(6));
    }

    /**
     * 测试 remove_ectopic_beats（Malik 规则）。
     *
     * <p>Python 原版测试：
     * <pre>
     * input    = [100, 110, 100, 130, 100, 100, 70, 100, 120, 100]
     * expected = [100, 110, 100, NaN, 100, 100, NaN, 100, 120, 100]
     * </pre>
     *
     * <p>Malik 规则：|RRi - RRi+1| &lt;= 0.2 * RRi
     * 100 → 130：|100 - 130| = 30 > 0.2 * 100 = 20，异位 → NaN
     * 100 → 70：|100 - 70| = 30 > 0.2 * 100 = 20，异位 → NaN
     */
    @Test
    public void testRemoveEctopicBeatsMalik() {
        List<Double> input = Arrays.asList(100.0, 110.0, 100.0, 130.0, 100.0, 100.0, 70.0, 100.0, 120.0, 100.0);
        List<Double> result = HrvPreprocessing.removeEctopicBeats(input, "malik", 0.2, false);

        assertEquals(10, result.size());
        assertEquals(100.0, result.get(0));
        assertEquals(110.0, result.get(1));
        assertEquals(100.0, result.get(2));
        assertTrue(Double.isNaN(result.get(3)), "130 应被标记为异位 NaN");
        assertEquals(100.0, result.get(4));
        assertEquals(100.0, result.get(5));
        assertTrue(Double.isNaN(result.get(6)), "70 应被标记为异位 NaN");
        assertEquals(100.0, result.get(7));
        assertEquals(120.0, result.get(8));
        assertEquals(100.0, result.get(9));
    }

    /**
     * 测试 remove_ectopic_beats（Kamath 规则）。
     *
     * <p>Python 原版测试：
     * <pre>
     * input    = [101, 110, 100, 140, 100, 100, 70, 100, 130, 115, 100, 78]
     * expected = [101, 110, 100, NaN, 100, 100, NaN, 100, 130, 115, 100, 78]
     * </pre>
     */
    @Test
    public void testRemoveEctopicBeatsKamath() {
        List<Double> input = Arrays.asList(101.0, 110.0, 100.0, 140.0, 100.0, 100.0, 70.0, 100.0, 130.0, 115.0, 100.0, 78.0);
        List<Double> result = HrvPreprocessing.removeEctopicBeats(input, "kamath", 0.2, false);

        assertEquals(12, result.size());
        assertEquals(101.0, result.get(0));
        assertEquals(110.0, result.get(1));
        assertEquals(100.0, result.get(2));
        assertTrue(Double.isNaN(result.get(3)), "140 应被标记为异位 NaN");
        assertEquals(100.0, result.get(4));
        assertEquals(100.0, result.get(5));
        assertTrue(Double.isNaN(result.get(6)), "70 应被标记为异位 NaN");
        assertEquals(100.0, result.get(7));
        assertEquals(130.0, result.get(8));
        assertEquals(115.0, result.get(9));
        assertEquals(100.0, result.get(10));
        assertEquals(78.0,  result.get(11));
    }

    /**
     * 测试 remove_ectopic_beats（Karlsson 规则）。
     *
     * <p>Python 原版测试：
     * <pre>
     * input    = [110, 100, 125, 100, 100, 70, 100, 130, 105, 100, 78, 100]
     * expected = [110, 100, NaN, 100, 100, NaN, 100, NaN, 105, 100, NaN, 100]
     * </pre>
     */
    @Test
    public void testRemoveEctopicBeatsKarlsson() {
        List<Double> input = Arrays.asList(110.0, 100.0, 125.0, 100.0, 100.0, 70.0, 100.0, 130.0, 105.0, 100.0, 78.0, 100.0);
        List<Double> result = HrvPreprocessing.removeEctopicBeats(input, "karlsson", 0.2, false);

        assertEquals(12, result.size());
        assertEquals(110.0, result.get(0));
        assertEquals(100.0, result.get(1));
        assertTrue(Double.isNaN(result.get(2)),  "125 应被 Karlsson 规则标记为 NaN");
        assertEquals(100.0, result.get(3));
        assertEquals(100.0, result.get(4));
        assertTrue(Double.isNaN(result.get(5)),  "70 应被 Karlsson 规则标记为 NaN");
        assertEquals(100.0, result.get(6));
        assertTrue(Double.isNaN(result.get(7)),  "130 应被 Karlsson 规则标记为 NaN");
        assertEquals(105.0, result.get(8));
        assertEquals(100.0, result.get(9));
        assertTrue(Double.isNaN(result.get(10)), "78 应被 Karlsson 规则标记为 NaN");
        assertEquals(100.0, result.get(11));
    }

    /**
     * 测试 remove_ectopic_beats（Acar 规则）。
     *
     * <p>Python 原版测试：
     * <pre>
     * input    = [100, 100, 100, 100, 100, 100, 100, 100, 110, 930, 110, 100, 10]
     * expected = [100, 100, 100, 100, 100, 100, 100, 100, 110, NaN, 110, 100, NaN]
     * </pre>
     *
     * <p>Acar 规则：前 9 个直接保留；第 10 个（930）与前 9 均值（约 100~110）差异超 20% → NaN
     */
    @Test
    public void testRemoveEctopicBeatsAcar() {
        List<Double> input = Arrays.asList(100.0, 100.0, 100.0, 100.0, 100.0, 100.0, 100.0, 100.0, 110.0, 930.0, 110.0, 100.0, 10.0);
        List<Double> result = HrvPreprocessing.removeEctopicBeats(input, "acar", 0.2, false);

        assertEquals(13, result.size());
        // 前 9 个直接保留
        for (int i = 0; i < 9; i++) {
            assertFalse(Double.isNaN(result.get(i)), "前 9 个元素不应为 NaN，索引=" + i);
        }
        assertTrue(Double.isNaN(result.get(9)),  "930 应被 Acar 规则标记为 NaN");
        assertEquals(110.0, result.get(10));
        assertEquals(100.0, result.get(11));
        assertTrue(Double.isNaN(result.get(12)), "10 应被 Acar 规则标记为 NaN");
    }

    /**
     * 测试 get_nn_intervals 完整预处理流水线。
     *
     * <p>Python 原版测试：
     * <pre>
     * input    = [700, 600, 2300, 1000, 1000, 230, 1200]
     * expected = [700, 600, 800, 1000, 1000, 1100, 1200]
     * </pre>
     *
     * <p>流程：
     * 1. removeOutliers：2300→NaN, 230→NaN
     * 2. interpolateNanValues：NaN 线性插值
     * 3. removeEctopicBeats（kamath）
     * 4. interpolateNanValues：再次插值
     */
    @Test
    public void testGetNnIntervals() {
        List<Double> input    = Arrays.asList(700.0, 600.0, 2300.0, 1000.0, 1000.0, 230.0, 1200.0);
        List<Double> expected = Arrays.asList(700.0, 600.0, 800.0,  1000.0, 1000.0, 1100.0, 1200.0);

        List<Double> result = HrvPreprocessing.getNnIntervals(input, 300, 2000, null, "forward",
                "linear", "kamath", false);

        assertEquals(expected.size(), result.size());
        for (int i = 0; i < expected.size(); i++) {
            assertEquals(expected.get(i), result.get(i), 1e-6,
                    "索引 " + i + " 处不匹配：期望 " + expected.get(i) + "，实际 " + result.get(i));
        }
    }
}
