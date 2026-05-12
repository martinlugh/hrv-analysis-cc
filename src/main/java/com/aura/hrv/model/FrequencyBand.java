package com.aura.hrv.model;

/**
 * 频率频带模型类，用于描述 HRV 频域分析中的 VLF、LF、HF 频段范围。
 * 对应 Python 中的 namedtuple VlfBand / LfBand / HfBand。
 */
public class FrequencyBand {

    /** 频带下限（Hz） */
    public final double low;

    /** 频带上限（Hz） */
    public final double high;

    /**
     * 构造频率频带。
     *
     * @param low  频带下限（Hz）
     * @param high 频带上限（Hz）
     */
    public FrequencyBand(double low, double high) {
        this.low = low;
        this.high = high;
    }

    /** 预定义：极低频段 VLF (0.003 ~ 0.04 Hz) */
    public static final FrequencyBand VLF = new FrequencyBand(0.003, 0.04);

    /** 预定义：低频段 LF (0.04 ~ 0.15 Hz) */
    public static final FrequencyBand LF = new FrequencyBand(0.04, 0.15);

    /** 预定义：高频段 HF (0.15 ~ 0.40 Hz) */
    public static final FrequencyBand HF = new FrequencyBand(0.15, 0.40);

    @Override
    public String toString() {
        return "FrequencyBand{low=" + low + ", high=" + high + "}";
    }
}
