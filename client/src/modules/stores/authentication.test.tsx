/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {rest} from 'msw';
import {authenticationStore} from './authentication';
import {nodeMockServer} from 'modules/mockServer/nodeMockServer';
import {getStateLocally} from 'modules/utils/localStorage';

describe('authentication store', () => {
  afterEach(() => {
    authenticationStore.reset();
  });

  it('should assume that there is an existing session', () => {
    expect(authenticationStore.status).toBe('initial');
  });

  it('should login', async () => {
    nodeMockServer.use(
      rest.post('/api/login', (_, res, ctx) => res.once(ctx.text(''))),
    );

    authenticationStore.disableSession();

    expect(authenticationStore.status).toBe('session-invalid');

    await authenticationStore.handleLogin('demo', 'demo');

    expect(authenticationStore.status).toBe('logged-in');
  });

  it('should handle login failure', async () => {
    nodeMockServer.use(
      rest.post('/api/login', (_, res, ctx) =>
        res.once(ctx.status(401), ctx.text('')),
      ),
    );

    expect(await authenticationStore.handleLogin('demo', 'demo')).toStrictEqual(
      {
        response: null,
        error: {
          variant: 'failed-response',
          response: expect.objectContaining({
            status: 401,
          }),
          networkError: null,
        },
      },
    );
    expect(authenticationStore.status).toBe('initial');
  });

  it('should logout', async () => {
    const mockReload = vi.fn();
    vi.spyOn(window, 'location', 'get').mockReturnValue({
      ...window.location,
      reload: mockReload,
    });
    window.clientConfig = {
      canLogout: true,
      isLoginDelegated: false,
    };

    nodeMockServer.use(
      rest.post('/api/login', (_, res, ctx) => res.once(ctx.text(''))),
    );
    nodeMockServer.use(
      rest.post('/api/logout', (_, res, ctx) => res.once(ctx.text(''))),
    );

    await authenticationStore.handleLogin('demo', 'demo');

    expect(authenticationStore.status).toBe('logged-in');

    await authenticationStore.handleLogout();

    expect(authenticationStore.status).toBe('logged-out');

    expect(mockReload).toHaveBeenCalledTimes(0);
    expect(getStateLocally('wasReloaded')).toBe(false);
  });

  it('should throw an error on logout failure', async () => {
    nodeMockServer.use(
      rest.post('/api/logout', (_, res, ctx) =>
        res.once(ctx.status(500), ctx.text('')),
      ),
    );

    expect(await authenticationStore.handleLogout()).not.toBeUndefined();
  });

  it('should disable session', async () => {
    const mockReload = vi.fn();
    vi.spyOn(window, 'location', 'get').mockReturnValue({
      ...window.location,
      reload: mockReload,
    });
    window.clientConfig = {
      canLogout: true,
      isLoginDelegated: false,
    };

    authenticationStore.activateSession();

    expect(authenticationStore.status).toBe('logged-in');

    authenticationStore.disableSession();

    expect(authenticationStore.status).toBe('session-expired');

    expect(mockReload).toHaveBeenCalledTimes(0);
    expect(getStateLocally('wasReloaded')).toBe(false);

    authenticationStore.activateSession();

    expect(authenticationStore.status).toBe('logged-in');

    expect(mockReload).toHaveBeenCalledTimes(0);
    expect(getStateLocally('wasReloaded')).toBe(false);
  });

  [{canLogout: false}, {canLogout: true, isLoginDelegated: true}].forEach(
    (value) => {
      const {canLogout, isLoginDelegated} = value;

      describe(`when canLogout is ${canLogout} and isLoginDelegated is ${isLoginDelegated}`, () => {
        it('should disable session', async () => {
          const mockReload = vi.fn();
          vi.spyOn(window, 'location', 'get').mockReturnValue({
            ...window.location,
            reload: mockReload,
          });
          window.clientConfig = {
            canLogout,
            isLoginDelegated,
          };

          authenticationStore.activateSession();

          expect(authenticationStore.status).toBe('logged-in');

          authenticationStore.disableSession();

          expect(authenticationStore.status).toBe(
            'invalid-third-party-session',
          );

          expect(mockReload).toHaveBeenCalledTimes(1);
          expect(getStateLocally('wasReloaded')).toBe(true);

          authenticationStore.activateSession();

          expect(authenticationStore.status).toBe('logged-in');

          expect(mockReload).toHaveBeenCalledTimes(1);
          expect(getStateLocally('wasReloaded')).toBe(false);
        });
      });
    },
  );

  [{canLogout: false}, {canLogout: true, isLoginDelegated: true}].forEach(
    (value) => {
      const {canLogout, isLoginDelegated} = value;

      describe(`when canLogout is ${canLogout} and isLoginDelegated is ${isLoginDelegated}`, () => {
        it('should logout', async () => {
          const mockReload = vi.fn();
          vi.spyOn(window, 'location', 'get').mockReturnValue({
            ...window.location,
            reload: mockReload,
          });
          window.clientConfig = {
            canLogout,
            isLoginDelegated,
          };

          nodeMockServer.use(
            rest.post('/api/login', (_, res, ctx) => res.once(ctx.text(''))),
          );
          nodeMockServer.use(
            rest.post('/api/logout', (_, res, ctx) => res.once(ctx.text(''))),
          );
          nodeMockServer.use(
            rest.post('/api/login', (_, res, ctx) => res.once(ctx.text(''))),
          );

          await authenticationStore.handleLogin('demo', 'demo');

          expect(authenticationStore.status).toBe('logged-in');

          await authenticationStore.handleLogout();

          expect(authenticationStore.status).toBe(
            'invalid-third-party-session',
          );

          expect(mockReload).toHaveBeenCalledTimes(1);
          expect(getStateLocally('wasReloaded')).toBe(true);

          await authenticationStore.handleLogin('demo', 'demo');

          expect(authenticationStore.status).toBe('logged-in');

          expect(mockReload).toHaveBeenCalledTimes(1);
          expect(getStateLocally('wasReloaded')).toBe(false);
        });
      });
    },
  );
});
