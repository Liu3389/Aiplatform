# 项目优化与修复报告汇总

本目录存放了项目开发过程中产生的各类优化、修复及审查报告。

## 📂 报告清单

### 1. 潜在问题与性能优化 (最新)
- **[POTENTIAL_ISSUES_AND_PERFORMANCE_REPORT.md](./POTENTIAL_ISSUES_AND_PERFORMANCE_REPORT.md)**
  - **内容**: 深度代码审查报告，涵盖功能性缺失、性能瓶颈（数据库/缓存/线程池）及安全性建议。
  - **状态**: ✅ 已修复并归档。

### 2. 系统优化总结
- **[OPTIMIZATION_COMPLETION_SUMMARY.md](./OPTIMIZATION_COMPLETION_SUMMARY.md)**
  - **内容**: 系统全面优化的完成总结，包括架构调整、性能提升指标及后续维护建议。

### 3. 业务逻辑与计费体系
- **[BUSINESS_LOGIC_PACKAGE_SYSTEM.md](./BUSINESS_LOGIC_PACKAGE_SYSTEM.md)**
  - **内容**: 详细描述了点数计费、套餐订阅及双轨制计费体系的实现逻辑。
- **[DAILY_POINTS_SETUP.md](./DAILY_POINTS_SETUP.md)**
  - **内容**: 每日点数发放、清空机制及免费额度配置的详细说明。
- **[POINTS_BASED_CHAT_GUIDE.md](./POINTS_BASED_CHAT_GUIDE.md)**
  - **内容**: 基于点数的对话结算流程、预扣与实扣逻辑指南。

### 4. 关键修复记录
- **[TRANSACTIONAL_ROLLBACK_FIX_COMPLETE.md](./TRANSACTIONAL_ROLLBACK_FIX_COMPLETE.md)**
  - **内容**: 事务回滚机制的全面核查与修复记录，确保数据一致性。
- **[CONSISTENCY_CHECK_REPORT.md](./CONSISTENCY_CHECK_REPORT.md)**
  - **内容**: 系统数据一致性检查报告，涵盖缓存与数据库同步策略。

### 5. 配置与集成指南
- **[DEEPSEEK_SETUP_GUIDE.md](./DEEPSEEK_SETUP_GUIDE.md)**
  - **内容**: DeepSeek API 的集成配置、模型选择及 Key 管理指南。
- **[REDIS_OPTIMIZATION.md](./REDIS_OPTIMIZATION.md)**
  - **内容**: Redis 缓存策略优化记录，包括 Lua 脚本原子操作及防击穿方案。

---

**说明**: 为了保持根目录整洁，所有非即时性的技术文档均已迁移至 `docs/reports/` 目录下进行分类管理。
