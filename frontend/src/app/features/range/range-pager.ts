import { formatRange, todayIso } from '../../core/dates/dates';
import { Pager } from '../shell/shell-header';
import { RangeConfig, RangeStore } from './range.store';

/** Builds the ShellHeader pager for a range store. Call inside a computed so it follows the store's signals. */
export function buildRangePager(store: RangeStore, config: RangeConfig, today: string = todayIso()): Pager {
  const start = store.start();
  const twoDays = config.unitLabel === '2 days';
  return {
    label: twoDays && start === today ? 'Today & tomorrow' : formatRange(start, store.end()),
    prevLabel: `Previous ${config.unitLabel}`,
    nextLabel: `Next ${config.unitLabel}`,
    todayDisabled: store.containsToday(),
    prev: () => store.go(-1),
    next: () => store.go(1),
    today: () => store.goToday(),
  };
}
