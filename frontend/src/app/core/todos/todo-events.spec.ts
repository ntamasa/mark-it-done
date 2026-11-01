import { TestBed } from '@angular/core/testing';
import { TodoEvents } from './todo-events';

describe('TodoEvents', () => {
  it('starts at 0 and increments on every change', () => {
    const events = TestBed.inject(TodoEvents);
    expect(events.version()).toBe(0);
    events.changed();
    events.changed();
    expect(events.version()).toBe(2);
  });
});
