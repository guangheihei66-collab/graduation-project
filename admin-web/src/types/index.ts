export type Id = string;
export type Role = "STUDENT" | "LAB_ADMIN" | "SYS_ADMIN";
export type AdminRole = Exclude<Role, "STUDENT">;
export type Category = "INSTRUMENT" | "COMPUTER" | "TOOL" | "OTHER";
export type EquipmentStatus = "AVAILABLE" | "MAINTENANCE" | "DISABLED";
export type ReservationStatus =
  | "PENDING"
  | "APPROVED"
  | "REJECTED"
  | "CANCELLED"
  | "EXPIRED"
  | "COMPLETED";
export type ReservationAction = "APPROVE" | "REJECT" | "REVOKE" | "COMPLETE";
export interface ApiResponse<T> {
  code: string;
  message: string;
  data: T;
  requestId: string;
}
export interface Page<T> {
  records: T[];
  total: number;
  page: number;
  size: number;
}
export interface PageParams {
  page: number;
  size: number;
}
export interface CurrentUser {
  id: Id;
  username: string;
  realName: string;
  role: Role;
}
export interface ManagedUser extends CurrentUser {
  enabled: number;
  createdAt: string;
  updatedAt: string;
}
export interface Lab {
  id: Id;
  code: string;
  name: string;
  location: string;
  description: string | null;
  enabled: number;
  createdAt: string;
  updatedAt: string;
}
export interface Equipment {
  id: Id;
  labId: Id;
  labName: string;
  assetNo: string;
  name: string;
  category: Category;
  model: string | null;
  location: string;
  description: string | null;
  status: EquipmentStatus;
  createdAt: string;
  updatedAt: string;
}
export interface ReservationLog {
  id: Id;
  operatorId: Id | null;
  action: string;
  fromStatus: ReservationStatus | null;
  toStatus: ReservationStatus;
  reason: string | null;
  createdAt: string;
}
export interface Reservation {
  id: Id;
  equipmentId: Id;
  equipmentName: string;
  labId: Id;
  studentId: Id;
  studentName: string;
  username: string;
  startTime: string;
  endTime: string;
  purpose: string;
  status: ReservationStatus;
  createdAt: string;
  updatedAt: string;
  logs: ReservationLog[];
}
export interface Statistics {
  totalCount: number;
  pendingCount: number;
  approvedCount: number;
  dailyTrend: { date: string; count: number }[];
  topEquipment: { equipmentId: Id; equipmentName: string; count: number }[];
}
export interface StatsParams {
  fromDate: string;
  toDate: string;
  labId?: Id;
}
export interface LabParams extends PageParams {
  name?: string;
  enabled?: number;
}
export interface EquipmentParams extends PageParams {
  labId?: Id;
  keyword?: string;
  category?: Category;
  status?: EquipmentStatus;
}
export interface UserParams extends PageParams {
  username?: string;
  role?: Role;
  enabled?: number;
}
export interface ReservationParams extends PageParams {
  labId?: Id;
  equipmentId?: Id;
  status?: ReservationStatus;
  fromDate?: string;
  toDate?: string;
}
export type LabEditable = Pick<Lab, "name" | "location" | "description">;
export type EquipmentEditable = Pick<
  Equipment,
  "name" | "category" | "model" | "location" | "description"
>;
export interface UserCreate {
  username: string;
  realName: string;
  role: Role;
  initialPassword: string;
}
export interface AdminAssignment {
  id: Id;
  realName: string;
}
