import axios, { type AxiosRequestConfig } from "axios";
import type { ApiResponse } from "../types";
import { tokenStorage } from "../utils/session";
export class ApiError extends Error {
  constructor(
    message: string,
    public readonly status = 0,
    public readonly code = "UNKNOWN",
    public readonly requestId = "",
  ) {
    super(message);
    this.name = "ApiError";
  }
}
let unauthorized = (): void => {
  tokenStorage.clear();
};
export function onUnauthorized(handler: () => void): void {
  unauthorized = handler;
}
export function normalizeError(error: unknown): ApiError {
  if (error instanceof ApiError) return error;
  if (!axios.isAxiosError<Partial<ApiResponse<unknown>>>(error))
    return new ApiError("操作未完成，请稍后重试");
  const status = error.response?.status ?? 0;
  const code = error.response?.data?.code ?? "NETWORK_ERROR";
  const messages: Record<number, string> = {
    400: "输入信息不符合要求，请检查后重试",
    401: "账号凭据不正确或会话已失效，请重新登录",
    403: "权限不足，无法执行此操作",
    404: "资源不存在或已不可访问",
    409: "业务冲突，请刷新后检查",
    503: "服务暂时不可用，请稍后手动重试",
  };
  const message =
    code === "INVALID_STATE"
      ? "预约状态或时间已变化，已重新加载，请检查后重试"
      : code === "RESOURCE_UNAVAILABLE"
        ? "资源不可用或存在有效预约，请先检查并处理相关预约"
        : (messages[status] ??
          (error.code === "ECONNABORTED"
            ? "请求超时，请检查网络后手动重试"
            : status === 0
              ? "无法连接服务器，请检查网络或服务状态"
              : "服务器处理失败，请稍后重试"));
  return new ApiError(
    message,
    status,
    code,
    error.response?.data?.requestId ?? "",
  );
}
export const http = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL || "/api/v1",
  timeout: 15000,
});
http.interceptors.request.use((config) => {
  const token = tokenStorage.get();
  if (token && config.url !== "/auth/login")
    config.headers.Authorization = `Bearer ${token}`;
  return config;
});
http.interceptors.response.use(
  (response) => response,
  (error: unknown) => {
    const result = normalizeError(error);
    if (result.status === 401) {
      tokenStorage.clear();
      unauthorized();
    }
    return Promise.reject(result);
  },
);
export async function request<T>(config: AxiosRequestConfig): Promise<T> {
  const response = await http.request<ApiResponse<T>>(config);
  if (response.data.code !== "OK")
    throw new ApiError(
      "服务返回了未成功的结果",
      response.status,
      response.data.code,
      response.data.requestId,
    );
  return response.data.data;
}
export function errorMessage(error: unknown): string {
  return error instanceof ApiError
    ? error.message
    : error instanceof Error
      ? error.message
      : "操作未完成，请重试";
}
