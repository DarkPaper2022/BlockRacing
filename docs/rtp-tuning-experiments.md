# RTP 参数联合调优实验

## 范围与可复现身份

- 目标：在本机一服两客户端真实 E2E 下，联合比较 Paper worker、BlockRacing 全局
  chunk inflight 上限和 JVM `Xmx`；服务端只 mock 目标列表生成，RTP/区块生成仍走生产逻辑。
- 目标机器：Intel i7-12700H（14 核、20 线程），31 GiB 内存，本机串行运行。
- framework：`/home/darkpaper/Documents/Docker_playground/agent-sandbox/workspace/parallel_smt/benchmarker`，
  commit `c7cb30afec031dcc31ca8bc56f2953b1b995237c`。
- bench：`bench/rtp-tuning`，commit `c7f09b6ff48d60511b3fbec5322899cde8ac2515`。
- algo：commit `00d2d7ca4edcafd3b4f0e19b8dafcc7dd0ea3348`；客户端 gitlink
  `b625853fadd9f8ff1dd6868b14c6225ec7ba7187`。
- dataset：`rtp_doe_v1`，catalog 为
  `bench/rtp-tuning/bench/datasets/rtp_doe_v1/catalog.sqlite`。
- 世界与随机性：每项从同一个 `target/e2e-runtime-no-start-gate` 模板复制；level seed 与
  RTP seed 固定；每项结束后删除复制出的 runtime。
- 资源形状：`runtime.max_active_runs=1`、`run.parallelism=1`、每项 timeout 1200 秒；
  本机 profile 不使用 `systemd-run`，避免资源限额改变被测形状。

主性能指标来自服务端真实日志：从候选 center `getChunkAtAsync` 发起，到 view-distance=12
的 625 个 chunks 全部完成并进入 `READY_POOL`。每项取最先完成的三个候选，报告首个耗时、
前三中位数和前三最大值。双客户端 PASS、实际 worker 数、625 chunks 和日志 inflight 值均为
correctness gate。

## R1：直接候选计时 smoke

- 目的：确认直接指标和 extractor 能工作，并检查 Paper 实际 worker 数。
- 配置：worker=7、inflight=6、Xmx=4G、Xms=1G。
- run key：`86842791085c37c055ad348eefd884546cb09551d6827715ff175b19439b9c76`。
- 结果：通过；Paper 实际报告 7 workers，双客户端 PASS。共记录 5 个完整候选；前三耗时
  10,950 / 20,935 / 34,000 ms，中位数 20,935 ms，最大值 34,000 ms。E2E 墙钟
  92.267 秒，服务端峰值 RSS 3,133,628 KiB。
- 结论：直接 candidate 指标可稳定提取，可以开始联合矩阵。

## R2：2×2×2 全因子矩阵

- 目的：分离 worker（5/7）、inflight（4/6）、Xmx（2G/4G）的主效应和交互。
- 方法：8 个全因子 item；首尾各增加一次 auto/4/2G 漂移控制。全部使用同一冻结世界、
  固定 seed、Xms=1G，串行运行。
- run key：`98a01d4687de515bfc38fef00966638cdb7888ff6b3254669f4ee6e5388562e6`。
- 运行时间：2026-09-29 12:41:20–12:58:50（Asia/Shanghai），约 17 分 30 秒。
- 状态：10 success，0 failed，0 timeout，所有 item 双客户端 PASS。

| worker | inflight | Xmx | 前三中位数 ms | 前三最大值 ms | server CPU | server 峰值 RSS KiB |
| ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| auto→5（首） | 4 | 2G | 31,749 | 53,300 | 485% | 2,879,476 |
| 7 | 6 | 4G | 20,650 | 33,851 | 597% | 3,568,008 |
| 5 | 4 | 2G | 31,699 | 53,050 | 474% | 2,892,148 |
| 7 | 4 | 2G | 29,847 | 48,850 | 541% | 3,072,440 |
| 5 | 6 | 4G | 22,499 | 38,450 | 539% | 3,127,940 |
| 5 | 4 | 4G | 32,164 | 53,700 | 456% | 3,093,388 |
| 7 | 6 | 2G | 20,800 | 34,099 | 620% | 3,111,472 |
| 5 | 6 | 2G | 22,199 | 38,900 | 566% | 2,814,908 |
| 7 | 4 | 4G | 30,240 | 49,000 | 525% | 3,469,056 |
| auto→5（末） | 4 | 2G | 32,300 | 55,350 | 474% | 2,829,492 |

全因子主效应（对另两个变量取平均）：

- inflight 4→6：前三中位数下降 30.5%，最大值下降 29.0%；这是最大、且各切片一致的收益。
- worker 5→7：前三中位数下降 6.5%，最大值下降 9.9%；CPU 平均增加约 12.2%。
- Xmx 2G→4G：前三中位数增加 1.0%，最大值几乎不变（+0.06%）；没有加速信号，
  峰值 RSS 平均增加约 11.5%。
- 首尾 auto 基线漂移：中位数 +1.7%，最大值 +3.8%，明显小于 inflight 效应。

综合推荐：

1. 本机 E2E 追求速度时使用 worker=7、inflight=6、Xmx=2G。相对显式 5/4/2G，
   前三中位数从 31.699 秒降到 20.800 秒（-34.4%），最大值从 53.050 秒降到
   34.099 秒（-35.7%）；E2E 墙钟从 117.486 秒降到 92.276 秒（-21.5%）。服务端
   峰值 RSS 增加约 214 MiB，CPU 从 474% 增至 620%。
2. 若更重视 CPU/内存余量，worker=5、inflight=6、Xmx=2G 是平衡配置：相对最快组，
   中位数慢 6.7%、最大值慢 14.1%，但峰值 RSS 少约 290 MiB，CPU 低约 54 个百分点。
3. `Xmx=4G` 不应作为区块生成加速项。本结果只覆盖约两分钟的隔离 E2E；正式长期开服是否保留
   4G 应由在线人数、长时堆占用和 GC 日志决定，不能仅凭本 slice 下调。

## 结果路径

- metadata：`bench/rtp-tuning/.bench-local/runs/98a01d4687de515bfc38fef00966638cdb7888ff6b3254669f4ee6e5388562e6/metadata.json`
- SQLite：`bench/rtp-tuning/.bench-local/runs/98a01d4687de515bfc38fef00966638cdb7888ff6b3254669f4ee6e5388562e6/results.sqlite`
- item 日志/artifacts：`bench/rtp-tuning/.bench-local/runs/98a01d4687de515bfc38fef00966638cdb7888ff6b3254669f4ee6e5388562e6/items/`

置信度为 **slice**：固定 seed/世界和首尾漂移控制足以支持本机局部选型，但每个全因子点只有一次；
若要给正式服做容量承诺，应再对候选配置和基线各做至少 3 次长时复测并加入 GC 日志。
