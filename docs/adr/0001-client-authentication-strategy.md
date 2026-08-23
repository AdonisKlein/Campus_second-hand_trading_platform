# Web 使用服务端会话，原生客户端使用可撤销令牌

Web 端采用 HttpOnly Cookie 与共享服务端 Session，以获得可靠的注销、封禁、CSRF 防护和凭据防窃取能力；未来 Windows 和移动端采用短期访问令牌与轮换刷新令牌。两种客户端共享账号和会话撤销规则，但不会为了复用 JWT 而把 Web 长期凭据暴露给 JavaScript。
