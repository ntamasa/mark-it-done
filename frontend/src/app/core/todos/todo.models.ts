export type Priority = 'URGENT' | 'HIGH' | 'MEDIUM' | 'LOW';

export interface Todo {
  id: string;
  name: string;
  description: string | null;
  /** YYYY-MM-DD */
  dueDate: string;
  /** HH:mm:ss, or null for all day */
  dueTime: string | null;
  priority: Priority;
  categoryId: string | null;
  done: boolean;
  /** ISO-8601 instant */
  doneAt: string | null;
  createdAt: string;
  updatedAt: string;
  expired: boolean;
  seriesId: string | null;
  /** YYYY-MM-DD */
  occurrenceDate: string | null;
  ruleSummary: string | null;
}

export interface TodoRequest {
  name: string;
  description?: string | null;
  dueDate: string;
  dueTime?: string | null;
  priority: Priority;
  categoryId?: string | null;
}

export type TagSeverity = 'danger' | 'warn' | 'info' | 'secondary';

export const PRIORITY_LABEL: Record<Priority, string> = {
  URGENT: 'Urgent',
  HIGH: 'High',
  MEDIUM: 'Medium',
  LOW: 'Low',
};

export const PRIORITY_SEVERITY: Record<Priority, TagSeverity> = {
  URGENT: 'danger',
  HIGH: 'warn',
  MEDIUM: 'info',
  LOW: 'secondary',
};

export const PRIORITY_ICON: Record<Priority, string> = {
  URGENT: 'pi-angle-double-up',
  HIGH: 'pi-angle-up',
  MEDIUM: 'pi-minus',
  LOW: 'pi-angle-down',
};

export function isOverdue(t: Todo): boolean {
  return t.expired && !t.done;
}

export function isRecurring(t: Todo): boolean {
  return t.seriesId !== null;
}
