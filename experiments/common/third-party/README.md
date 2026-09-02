# 固定第三方清单

- `metrics-server-v0.9.0.yaml`
  - 来源：`https://github.com/kubernetes-sigs/metrics-server/releases/download/v0.9.0/components.yaml`
  - SHA-256：`1cec29a5267809306a2c6ec74a3e449abbb705b4a8beed0c8a1963910f72c79b`
  - 本地文件保持官方原文；Kind 所需的 `--kubelet-insecure-tls` 由安装脚本幂等追加，不修改供应商清单。

升级第三方清单时必须同时更新固定版本、SHA-256、兼容性验证和本文件，禁止改成 `latest` URL。
