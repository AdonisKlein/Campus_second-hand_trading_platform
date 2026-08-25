const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");

const scripts = path.resolve(__dirname, "../assets/js");
const api = fs.readFileSync(path.join(scripts, "api.js"), "utf8");
const register = fs.readFileSync(path.join(scripts, "register.js"), "utf8");
const profile = fs.readFileSync(path.join(scripts, "profile.js"), "utf8");

assert.match(api, /function redirectToLoginWithMessage\(message\)/,
    "api.js 必须集中提供带一次性消息的登录跳转");
assert.match(api, /sessionStorage\.setItem\("authSuccessMessage", message\)/,
    "登录跳转前必须保存一次性成功消息");
assert.match(register, /redirectToLoginWithMessage\('注册成功，请使用邮箱和密码登录'\)/,
    "注册成功后必须自动跳转登录");
assert.match(profile, /redirectToLoginWithMessage\("密码重置成功，请使用新密码登录"\)/,
    "重置密码成功后必须自动跳转登录");
assert.match(profile, /sessionStorage\.removeItem\("authSuccessMessage"\)/,
    "成功消息显示后必须立即消费，刷新时不得重复出现");

console.log("Auth success redirect contract passed.");
