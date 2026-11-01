import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { Category, itemCountLabel } from './category.models';
import { CategoryStore } from './category.store';

const work: Category = { id: 'w', name: 'Work', swatch: 'TEAL', itemCount: 3 };
const home: Category = { id: 'h', name: 'Home', swatch: 'PINK', itemCount: 0 };

describe('CategoryStore', () => {
  let store: CategoryStore;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    store = TestBed.inject(CategoryStore);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('loads categories and indexes them by id', () => {
    store.load();
    http.expectOne('/api/categories').flush([work, home]);
    expect(store.categories().map((c) => c.name)).toEqual(['Home', 'Work']);
    expect(store.byId().get('w')).toEqual(work);
  });

  it('create adds the category in sorted position', () => {
    store.load();
    http.expectOne('/api/categories').flush([work]);
    let result: Category | undefined;
    store.create({ name: 'Home', swatch: 'PINK' }).subscribe((c) => (result = c));
    const req = http.expectOne('/api/categories');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({ name: 'Home', swatch: 'PINK' });
    req.flush(home);
    expect(result).toEqual(home);
    expect(store.categories().map((c) => c.id)).toEqual(['h', 'w']);
  });

  it('update replaces the category immutably', () => {
    store.load();
    http.expectOne('/api/categories').flush([home, work]);
    const before = store.categories();
    store.update('h', { name: 'Zed', swatch: 'SKY' }).subscribe();
    const req = http.expectOne('/api/categories/h');
    expect(req.request.method).toBe('PUT');
    req.flush({ ...home, name: 'Zed', swatch: 'SKY' });
    expect(store.categories().map((c) => c.name)).toEqual(['Work', 'Zed']);
    expect(store.categories()).not.toBe(before);
  });

  it('requestDelete is a no-op', () => {
    store.requestDelete(work);
    expect(store.categories()).toEqual([]);
  });
});

describe('itemCountLabel', () => {
  it('formats the counts', () => {
    expect(itemCountLabel(0)).toBe('No todos');
    expect(itemCountLabel(1)).toBe('1 item');
    expect(itemCountLabel(3)).toBe('3 items');
  });
});
