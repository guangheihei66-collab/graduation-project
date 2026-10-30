import { request } from "./http";
import type {
  CurrentUser,
  Page,
  Lab,
  Equipment,
  ManagedUser,
  Reservation,
  Statistics,
  StatsParams,
  LabParams,
  EquipmentParams,
  UserParams,
  ReservationParams,
  LabEditable,
  EquipmentEditable,
  UserCreate,
  AdminAssignment,
  Id,
} from "../types";
export const authApi = {
  login: (username: string, password: string) =>
    request<{ accessToken: string; expiresIn: number; user: CurrentUser }>({
      method: "POST",
      url: "/auth/login",
      data: { username, password },
    }),
  me: () => request<CurrentUser>({ url: "/auth/me" }),
};
export const labsApi = {
  list: (params: LabParams) => request<Page<Lab>>({ url: "/labs", params }),
  detail: (id: Id) => request<Lab>({ url: `/labs/${id}` }),
  create: (data: LabEditable & { code: string }) =>
    request<Lab>({ method: "POST", url: "/labs", data }),
  update: (id: Id, data: Partial<LabEditable & { enabled: number }>) =>
    request<Lab>({ method: "PATCH", url: `/labs/${id}`, data }),
  admins: (id: Id) => request<AdminAssignment[]>({ url: `/labs/${id}/admins` }),
  assign: (id: Id, userIds: Id[]) =>
    request<AdminAssignment[]>({
      method: "PUT",
      url: `/labs/${id}/admins`,
      data: { userIds },
    }),
};
export const equipmentApi = {
  list: (params: EquipmentParams) =>
    request<Page<Equipment>>({ url: "/equipment", params }),
  detail: (id: Id) => request<Equipment>({ url: `/equipment/${id}` }),
  create: (data: EquipmentEditable & { labId: Id; assetNo: string }) =>
    request<Equipment>({ method: "POST", url: "/equipment", data }),
  update: (
    id: Id,
    data: Partial<EquipmentEditable & { status: Equipment["status"] }>,
  ) => request<Equipment>({ method: "PATCH", url: `/equipment/${id}`, data }),
};
export const usersApi = {
  list: (params: UserParams) =>
    request<Page<ManagedUser>>({ url: "/users", params }),
  create: (data: UserCreate) =>
    request<ManagedUser>({ method: "POST", url: "/users", data }),
  update: (id: Id, data: { realName?: string; enabled?: number }) =>
    request<ManagedUser>({ method: "PATCH", url: `/users/${id}`, data }),
  reset: (id: Id, newPassword: string) =>
    request<{ id: Id }>({
      method: "PUT",
      url: `/users/${id}/password`,
      data: { newPassword },
    }),
};
export const reservationsApi = {
  list: (params: ReservationParams) =>
    request<Page<Reservation>>({ url: "/reservations", params }),
  detail: (id: Id) => request<Reservation>({ url: `/reservations/${id}` }),
  review: (id: Id, decision: "APPROVE" | "REJECT", reason?: string) =>
    request<Reservation>({
      method: "POST",
      url: `/reservations/${id}/review`,
      data: { decision, ...(reason ? { reason } : {}) },
    }),
  revoke: (id: Id, reason: string) =>
    request<Reservation>({
      method: "POST",
      url: `/reservations/${id}/revoke`,
      data: { reason },
    }),
  complete: (id: Id) =>
    request<Reservation>({
      method: "POST",
      url: `/reservations/${id}/complete`,
    }),
};
export const statisticsApi = {
  overview: (params: StatsParams) =>
    request<Statistics>({ url: "/statistics/overview", params }),
};
export async function allPages<T>(
  fetch: (page: number) => Promise<Page<T>>,
): Promise<T[]> {
  const result: T[] = [];
  for (let page = 1; page <= 100; page++) {
    const part = await fetch(page);
    result.push(...part.records);
    if (result.length >= part.total || part.records.length === 0) return result;
  }
  throw new Error("可选数据超过一万条，请联系管理员缩小数据范围");
}
