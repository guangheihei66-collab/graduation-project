import { beforeEach, describe, it, expect, vi } from "vitest";
import {
  AxiosError,
  type AxiosResponse,
  type InternalAxiosRequestConfig,
} from "axios";
import {
  http,
  request,
  ApiError,
  normalizeError,
  onUnauthorized,
} from "../src/api/http";
import { tokenStorage } from "../src/utils/session";
import { labsApi, equipmentApi, usersApi, reservationsApi } from "../src/api";
import type { ApiResponse } from "../src/types";
const response = <T>(
  config: InternalAxiosRequestConfig,
  data: T,
  status = 200,
): AxiosResponse<ApiResponse<T>> => ({
  config,
  status,
  statusText: "OK",
  headers: {},
  data: { code: "OK", message: "成功", data, requestId: "test-request" },
});
beforeEach(() => {
  sessionStorage.clear();
  onUnauthorized(() => tokenStorage.clear());
});
describe("统一 API 层", () => {
  it("解析统一响应并保留大 ID 字符串", async () => {
    http.defaults.adapter = async (config) =>
      response(config, { id: "9007199254740993" });
    expect(await request({ url: "/labs/1" })).toEqual({
      id: "9007199254740993",
    });
  });
  it("附加 Bearer 但不在登录请求添加旧令牌", async () => {
    tokenStorage.set("test-only-token");
    const headers: unknown[] = [];
    http.defaults.adapter = async (config) => {
      headers.push(config.headers.Authorization);
      return response(config, {});
    };
    await request({ url: "/auth/me" });
    await request({ url: "/auth/login", method: "POST" });
    expect(headers).toEqual(["Bearer test-only-token", undefined]);
  });
  it("401 清除令牌并调用失效处理", async () => {
    tokenStorage.set("test-only");
    const expired = vi.fn();
    onUnauthorized(expired);
    http.defaults.adapter = async (config) => {
      throw new AxiosError(
        "expired",
        "ERR_BAD_REQUEST",
        config,
        undefined,
        response(config, null, 401),
      );
    };
    await expect(request({ url: "/auth/me" })).rejects.toMatchObject({
      status: 401,
    });
    expect(tokenStorage.get()).toBeNull();
    expect(expired).toHaveBeenCalledOnce();
  });
  it("403 不清理会话、不误当过期", async () => {
    tokenStorage.set("valid-test");
    const expired = vi.fn();
    onUnauthorized(expired);
    http.defaults.adapter = async (config) => {
      throw new AxiosError(
        "forbidden",
        "ERR_BAD_REQUEST",
        config,
        undefined,
        response(config, null, 403),
      );
    };
    await expect(request({ url: "/users" })).rejects.toMatchObject({
      status: 403,
      message: "权限不足，无法执行此操作",
    });
    expect(tokenStorage.get()).toBe("valid-test");
    expect(expired).not.toHaveBeenCalled();
  });
  it("409 INVALID_STATE 提示状态变化，写操作不自动重试", async () => {
    const adapter = vi.fn(async (config: InternalAxiosRequestConfig) => {
      const res = response(config, null, 409);
      res.data.code = "INVALID_STATE";
      throw new AxiosError(
        "conflict",
        "ERR_BAD_REQUEST",
        config,
        undefined,
        res,
      );
    });
    http.defaults.adapter = adapter;
    await expect(
      reservationsApi.review("123", "APPROVE"),
    ).rejects.toMatchObject({
      status: 409,
      code: "INVALID_STATE",
      message: expect.stringContaining("状态或时间已变化"),
    });
    expect(adapter).toHaveBeenCalledOnce();
  });
  it("网络、超时、未知错误均有安全提示", () => {
    expect(
      normalizeError(new AxiosError("secret url", "ECONNABORTED")).message,
    ).toContain("超时");
    expect(normalizeError(new AxiosError("raw detail")).message).toContain(
      "无法连接",
    );
    expect(normalizeError(new Error("sensitive-stack")).message).not.toContain(
      "sensitive",
    );
    expect(normalizeError(new ApiError("已规范化", 404)).status).toBe(404);
  });
  it("保留分页数字，查询参数保持页从1开始", async () => {
    let params: unknown;
    http.defaults.adapter = async (config) => {
      params = config.params;
      return response(config, { records: [], total: 12, page: 1, size: 20 });
    };
    expect(await labsApi.list({ page: 1, size: 20 })).toEqual({
      records: [],
      total: 12,
      page: 1,
      size: 20,
    });
    expect(params).toEqual({ page: 1, size: 20 });
  });
  it("请求体与后端契约一致且不混入不可变字段", async () => {
    const calls: { url?: string; data: unknown }[] = [];
    http.defaults.adapter = async (config) => {
      calls.push({ url: config.url, data: JSON.parse(String(config.data)) });
      return response(config, {});
    };
    await labsApi.update("9007199254740993", { description: null });
    await equipmentApi.update("42", { model: null, status: "MAINTENANCE" });
    await usersApi.reset("7", "test-only-new-password");
    await labsApi.assign("1", ["9007199254740993"]);
    await reservationsApi.review("4", "REJECT", "用途不符");
    expect(calls).toEqual([
      { url: "/labs/9007199254740993", data: { description: null } },
      { url: "/equipment/42", data: { model: null, status: "MAINTENANCE" } },
      {
        url: "/users/7/password",
        data: { newPassword: "test-only-new-password" },
      },
      { url: "/labs/1/admins", data: { userIds: ["9007199254740993"] } },
      {
        url: "/reservations/4/review",
        data: { decision: "REJECT", reason: "用途不符" },
      },
    ]);
  });
});
