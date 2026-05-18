# Heart Rate Variability analysis — Java Toolkit

> **Java 移植版**：纯 Java 工具包，不依赖 Spring Boot / 数据库 / Web 接口，可直接作为 Maven 依赖引用。
> 对应 Python hrv-analysis 项目，所有参数名、输出字段名与 Python 原版完全一致。

## Java 快速开始

### 构建 & 测试

```bash
mvn clean test
```

### 全量分析（推荐）

一次调用获得所有 HRV 特征 + 呼吸频率，无需单独调用各子模块：

```java
import com.aura.hrv.preprocessing.HrvPreprocessing;
import com.aura.hrv.features.HrvFullAnalysis;
import java.util.*;

List<Double> rr = Arrays.asList(700.0, 710.0, 2300.0, 690.0, 710.0, /* ... */);
List<Double> nn = HrvPreprocessing.getNnIntervals(rr);

// 一次调用，输出全部 34 个特征
Map<String, Object> all = HrvFullAnalysis.analyze(nn);

// 直接按 key 取值
System.out.println(all.get("mean_nni"));                  // 时域
System.out.println(all.get("lf"));                        // 频域
System.out.println(all.get("sd1"));                       // Poincaré
System.out.println(all.get("sampen"));                    // 样本熵
System.out.println(all.get("breathing_rate_hz"));         // 呼吸频率(Hz)
System.out.println(all.get("breathing_rate_per_minute")); // 呼吸频率(次/分)
```

**全量输出字段（共 34 个）：**

| 分类 | 字段 |
|---|---|
| 时域（16） | `mean_nni` `sdnn` `sdsd` `rmssd` `median_nni` `range_nni` `nni_50` `pnni_50` `nni_20` `pnni_20` `cvsd` `cvnni` `mean_hr` `max_hr` `min_hr` `std_hr` |
| 几何（2） | `triangular_index` `tinn` |
| 频域（7） | `vlf` `lf` `hf` `total_power` `lf_hf_ratio` `lfnu` `hfnu` |
| 非线性（6） | `sd1` `sd2` `ratio_sd2_sd1` `csi` `cvi` `Modified_csi` |
| 样本熵（1） | `sampen` |
| 呼吸频率（2） | `breathing_rate_hz` `breathing_rate_per_minute` |

### 单独调用（按需）

各子模块仍可独立使用，与全量分析结果完全一致：

### 使用示例

```java
import com.aura.hrv.preprocessing.HrvPreprocessing;
import com.aura.hrv.features.*;
import com.aura.hrv.model.FrequencyBand;
import java.util.*;

List<Double> rr = Arrays.asList(700.0, 710.0, 2300.0, 690.0, 710.0);
List<Double> nn = HrvPreprocessing.getNnIntervals(rr);

Map<String, Double> time = HrvTimeDomainFeatures.getTimeDomainFeatures(nn);
System.out.println("mean_nni=" + time.get("mean_nni"));

Map<String, Double> freq = HrvFrequencyDomainFeatures.getFrequencyDomainFeatures(nn);
System.out.println("lf=" + freq.get("lf") + " hf=" + freq.get("hf"));

Map<String, Double> nl = HrvNonLinearFeatures.getCsiCviFeatures(nn);
System.out.println("csi=" + nl.get("csi") + " Modified_csi=" + nl.get("Modified_csi"));
```

### 呼吸频率（HrvBreathingRate）

**算法流程（严格对应 HeartPy 内部逻辑）：**

```
RR interval (ms)
  → Step 2  累积时间轴：rr_x = cumsum(rr) / 1000，强制从 0 开始（秒）
  → Step 3  三次样条插值（cubic spline）→ 均匀采样信号 rr_interp（默认 4 Hz）
  → Step 4  去 DC：rr_normalized = rr_interp - mean(rr_interp)
  → Step 5  Welch PSD（Hann 窗，nfft=4096）
  → Step 6  掩膜：freqs >= 0.1 Hz & freqs <= 0.4 Hz
  → Step 7  主峰：breathingrate = freqs[argmax(psd[mask])]（Hz）
  → Step 8  换算：breathingratePerMinute = breathingrate × 60
```

**调用示例：**

```java
import com.aura.hrv.features.HrvBreathingRate;
import java.util.Map;

// 1. 默认参数（fs=4Hz，呼吸频段 0.1~0.4 Hz，三次样条插值）
Map<String, Double> br = HrvBreathingRate.getBreathingRate(nn_intervals);
System.out.println("breathing_rate_hz         = " + br.get("breathing_rate_hz"));
System.out.println("breathing_rate_per_minute = " + br.get("breathing_rate_per_minute"));

// 2. 自定义采样频率和频段
Map<String, Double> br2 = HrvBreathingRate.getBreathingRate(nn_intervals, 4, 0.1, 0.4);
```

**方法签名：**

```java
// 默认参数
Map<String, Double> getBreathingRate(List<Double> nn_intervals)

// 自定义参数
Map<String, Double> getBreathingRate(List<Double> nn_intervals,
                                     int    sampling_frequency,  // 默认 4
                                     double freqLow,             // 默认 0.1
                                     double freqHigh)            // 默认 0.4
```

**输出字段：**

| key | 说明 |
|---|---|
| `breathing_rate_hz` | 呼吸频率（Hz），0.1~0.4 Hz 内的 PSD 峰值频率 |
| `breathing_rate_per_minute` | 呼吸频率（次/分钟）= breathing_rate_hz × 60 |

**呼吸频率参考范围：**

| 呼吸次数/分钟 | 对应 Hz |
|---|---|
| 6  次/分钟 | 0.10 Hz |
| 12 次/分钟 | 0.20 Hz |
| 18 次/分钟 | 0.30 Hz |
| 24 次/分钟 | 0.40 Hz |

**注意：** 本模块不修改任何已有文件，独立新增。内部复用 `HrvFrequencyDomainFeatures.welchPsd()`，三次样条插值使用 Apache Commons Math `SplineInterpolator`。

### 精度说明

| 模块            | 误差范围  | 说明                                         |
|----------------|---------|---------------------------------------------|
| 时域/几何/非线性  | < 1e-10 | 与 Python 完全等价                            |
| 样本熵          | < 1e-6  | 对齐 nolds.sampen，r=0.2*std(ddof=0)         |
| 频域 Welch      | < 1e-3  | JTransforms 与 scipy.signal.welch 极小浮点差异 |
| 频域 Lomb       | 1%~5%   | 频率网格策略与 astropy autopower 存在差异       |

---

# Heart Rate Variability analysis

[![PyPI version](https://badge.fury.io/py/hrv-analysis.svg)](https://badge.fury.io/py/hrv-analysis)
[![Build Status](https://github.com/Aura-healthcare/hrv-analysis/actions/workflows/ci.yml/badge.svg)](https://github.com/Aura-healthcare/hrv-analysis/actions/workflows/ci.yml)
[![codecov](https://codecov.io/gh/Aura-healthcare/hrv-analysis/branch/master/graph/badge.svg)](https://codecov.io/gh/Aura-healthcare/hrv-analysis)
[![License: GPL v3](https://img.shields.io/badge/License-GPL%20v3-blue.svg)](https://www.gnu.org/licenses/gpl-3.0)
[![Downloads](https://pepy.tech/badge/hrv-analysis)](https://pepy.tech/project/hrv-analysis)

**hrvanalysis** is a Python module for Heart Rate Variability analysis of RR-intervals built on top of SciPy, AstroPy, Nolds and NumPy and distributed under the GPLv3 license.

The development of this library started in July 2018 as part of [Aura Healthcare](https://www.aura.healthcare) project, in [OCTO Technology](https://www.octo.com/fr) R&D team and is maintained by Robin Champseix.


![alt text](https://github.com/Aura-healthcare/hrv-analysis/blob/master/figures/timeserie_distrib_plot.png)

**Full documentation** : https://aura-healthcare.github.io/hrv-analysis

**Website** : https://www.aura.healthcare

**Github** : https://github.com/Aura-healthcare

**Version** : 1.0.4


## Installation / Prerequisites

#### User installation

The easiest way to install hrv-analysis is using ``pip`` :

    $ pip install hrv-analysis

you can also clone the repository:

    $ git clone https://github.com/Aura-healthcare/hrv-analysis.git
    $ cd hrv-analysis
    $ uv sync --all-extras

#### Dependencies

**hrvanalysis** requires the following:
- Python (>= 3.5)
- astropy >= 3.0.4
- future >= 0.16.0
- nolds >= 0.4.1
- numpy >= 1.15.1
- scipy >= 1.1.0

Note: The package can be used with all Python versions from 3.5 to latest version (currently Python 3.9).


## Getting started

### Outliers and ectopic beats filtering methods

This package provides methods to remove outliers and ectopic beats from signal for further analysis. Those methods are useful to get Normal to Normal Interval (NN-intervals) from RR-intervals.
Please use this methods carefully as they might have a huge impact on features calculation.

```python
from hrvanalysis import remove_outliers, remove_ectopic_beats, interpolate_nan_values

# rr_intervals_list contains integer values of RR-interval
rr_intervals_list = [1000, 1050, 1020, 1080, ..., 1100, 1110, 1060]

# This remove outliers from signal
rr_intervals_without_outliers = remove_outliers(rr_intervals=rr_intervals_list,  
                                                low_rri=300, high_rri=2000)
# This replace outliers nan values with linear interpolation
interpolated_rr_intervals = interpolate_nan_values(rr_intervals=rr_intervals_without_outliers,
                                                   interpolation_method="linear")

# This remove ectopic beats from signal
nn_intervals_list = remove_ectopic_beats(rr_intervals=interpolated_rr_intervals, method="malik")
# This replace ectopic beats nan values with linear interpolation
interpolated_nn_intervals = interpolate_nan_values(rr_intervals=nn_intervals_list)
```

You can find how to use the following methods, references and more details in the [documentation](https://aura-healthcare.github.io/hrv-analysis/tutorial.html):
- remove_outliers
- remove_ectopic_beats


### Features calculation

There are 4 types of features you can get from NN-intervals:

> Time domain features : **Mean_NNI, SDNN, SDSD, NN50, pNN50, NN20, pNN20, RMSSD, Median_NN, Range_NN, CVSD, CV_NNI, Mean_HR, Max_HR, Min_HR, STD_HR**

> Geometrical domain features : **Triangular_index, TINN**

> Frequency domain features : **LF, HF, VLF, LH/HF ratio, LFnu, HFnu, Total_Power**

> Non Linear domain features : **CSI, CVI, Modified_CSI, SD1, SD2, SD1/SD2 ratio, SampEn**

As an exemple, what you can compute to get Time domain analysis is :

```python
from hrvanalysis import get_time_domain_features

 # nn_intervals_list contains integer values of NN-interval
nn_intervals_list = [1000, 1050, 1020, 1080, ..., 1100, 1110, 1060]

time_domain_features = get_time_domain_features(nn_intervals_list)

>>> time_domain_features
{'mean_nni': 718.248,
 'sdnn': 43.113,
 'sdsd': 19.519,
 'nni_50': 24,
 'pnni_50': 2.4,
 'nni_20': 225,
 'pnni_20': 22.5,
 'rmssd': 19.519,
 'median_nni': 722.5,
 'range_nni': 249,
 'cvsd': 0.0272,
 'cvnni': 0.060,
 'mean_hr': 83.847,
 'max_hr': 101.694,
 'min_hr': 71.513,
 'std_hr': 5.196}
```

You can find how to use the following methods, references and details about each feature in the [documentation](https://aura-healthcare.github.io/hrv-analysis/tutorial.html):
- get_time_domain_features
- get_geometrical_features
- get_frequency_domain_features
- get_csi_cvi_features
- get_poincare_plot_features
- get_sampen


### Plot functions

There are several plot functions that allow you to see, for example, the Power Spectral Density (PSD) for frequency domain features or Poincaré Plot for non linear domain features:

```python
from hrvanalysis import plot_psd

# nn_intervals_list contains integer values of NN-interval
nn_intervals_list = [1000, 1050, 1020, 1080, ..., 1100, 1110, 1060]

plot_psd(nn_intervals_list, method="welch")
plot_psd(nn_intervals_list, method="lomb")
```

![alt text](https://github.com/Aura-healthcare/hrv-analysis/blob/master/figures/psd_periodogram_plot.png)


```python
from hrvanalysis import plot_poincare

# nn_intervals_list contains integer values of NN-interval
nn_intervals_list = [1000, 1050, 1020, 1080, ..., 1100, 1110, 1060]

plot_poincare(nn_intervals_list)
plot_poincare(nn_intervals_list, plot_sd_features=True)
```

![alt text](https://github.com/Aura-healthcare/hrv-analysis/blob/master/figures/poincare_plot.png)


You can find how to use methods and details in the [documentation](https://aura-healthcare.github.io/hrv-analysis/tutorial.html):
- plot_distrib
- plot_timeseries
- plot_psd
- plot_poincare


Here is a high level view of the distinct building blocks of the package:

![alt text](https://github.com/Aura-healthcare/hrv-analysis/blob/master/figures/architecture.png)


## References

Here are the main references used to compute the set of features and for signal processing methods:

> Heart rate variability - Standards of measurement, physiological interpretation, and clinical use, Task Force of The European Society of Cardiology and The North American Society of Pacing and Electrophysiology, 1996

> Signal Processing Methods for Heart Rate Variability - Gari D. Clifford, 2002

> Physiological time-series analysis using approximate entropy and sample entropy, Joshua S. Richman, J. Randall Moorman - 2000

> Using Lorenz plot and Cardiac Sympathetic Index of heart rate variability for detecting seizures for patients with epilepsy, Jesper Jeppesen et al, 2014


## Authors

**Robin Champseix** - (https://github.com/robinchampseix)


## License

This project is licensed under the *GNU GENERAL PUBLIC License* - see the [LICENSE.md](https://github.com/Aura-healthcare/hrv-analysis/blob/master/LICENSE) file for details

## How to contribute
Please refer to [How To Contribute](https://github.com/Aura-healthcare/hrv-analysis/blob/master/CONTRIBUTING.md)

## Acknowledgments

I hereby thank Laurent Ribière and Clément Le Couedic, my coworkers who gave me time to Open Source this library.
I also thank Fabien Arcellier for his advices on to how build a library in PyPi.
