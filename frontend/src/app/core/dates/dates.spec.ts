import {
  addDays,
  formatDay,
  formatLong,
  formatRange,
  formatShort,
  formatTime,
  fromIso,
  mondayOf,
  toIso,
  todayIso,
} from './dates';

describe('dates', () => {
  it('round-trips toIso and fromIso', () => {
    expect(toIso(fromIso('2026-02-28'))).toBe('2026-02-28');
    expect(toIso(new Date(2026, 0, 5))).toBe('2026-01-05');
  });

  it('todayIso uses the local calendar date', () => {
    expect(todayIso(new Date(2026, 9, 31, 23, 59))).toBe('2026-10-31');
  });

  it('adds days across month and year boundaries', () => {
    expect(addDays('2026-01-31', 1)).toBe('2026-02-01');
    expect(addDays('2025-12-31', 1)).toBe('2026-01-01');
    expect(addDays('2026-01-01', -1)).toBe('2025-12-31');
    expect(addDays('2024-02-28', 1)).toBe('2024-02-29');
  });

  it('adds days without drifting over DST changes', () => {
    expect(addDays('2026-03-28', 2)).toBe('2026-03-30');
    expect(addDays('2026-10-24', 2)).toBe('2026-10-26');
    expect(addDays('2026-03-08', 7)).toBe('2026-03-15');
    expect(addDays('2026-11-01', 1)).toBe('2026-11-02');
  });

  it('finds the Monday of a week', () => {
    expect(mondayOf('2026-10-21')).toBe('2026-10-19');
    expect(mondayOf('2026-10-19')).toBe('2026-10-19');
    expect(mondayOf('2026-10-25')).toBe('2026-10-19');
    expect(mondayOf('2026-01-01')).toBe('2025-12-29');
  });

  it('formats dates', () => {
    expect(formatShort('2026-10-21')).toBe('Oct 21');
    expect(formatDay('2026-10-21')).toBe('Wed, Oct 21');
    expect(formatLong('2026-10-21')).toBe('Wednesday, Oct 21');
  });

  it('formats ranges', () => {
    expect(formatRange('2026-10-19', '2026-10-25')).toBe('Oct 19 – Oct 25');
    expect(formatRange('2025-12-29', '2026-01-04')).toBe('Dec 29, 2025 – Jan 4, 2026');
  });

  it('formats times', () => {
    expect(formatTime('14:30:00')).toBe('14:30');
    expect(formatTime('09:05')).toBe('09:05');
    expect(formatTime(null)).toBe('All day');
  });
});
