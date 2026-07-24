/// <reference types="Cypress" />

const pages = [
  {
    url: '/sync-studio',
    contains: /sync studio/i,
  },
  {
    url: '/synapses',
    contains: /synapse studio/i,
  },
  {
    url: '/logs',
    contains: /logs/i,
  },
  {
    url: '/logs/transactions',
    contains: /transactions/i,
  },
  {
    url: '/logs/sync-errors',
    contains: /sync errors/i,
  },
  {
    url: '/settings',
    contains: /settings/i,
  },
  {
    url: '/settings',
    contains: /settings/i,
  },
  {
    url: '/settings/subscription-profile',
    contains: /subscription profile/i,
  },
  {
    url: '/settings/subscription',
    contains: /Subscriptions/i,
  },
  {
    url: '/settings/instance',
    contains: /Instances/i,
  },
  {
    url: '/settings/user',
    contains: /Users/i,
  },
  {
    url: '/settings/credential',
    contains: /Service Credentials/i,
  },
  {
    url: '/settings/specter',
    contains: /specter/i,
  },
  {
    url: '/settings/datastore',
    contains: /Data Store/i,
  },
  {
    url: '/edit-profile',
    contains: /edit profile/i,
  },
  {
    url: '/notifications',
    contains: /notifications/i,
  },
  {
    url: '/settings/sso',
    contains: /Single Sign-On/i,
  },
];

context('Navigate to all pages', () => {
  beforeEach(() => {
    const username = Cypress.env('username');
    const password = Cypress.env('password');
    cy.login(username, password, '/synapses');
  });

  it('.navigate to pages', () => {
    pages.forEach(page => {
      cy.visit(page.url);
      cy.url().should('include', page.url);
      cy.get('.ant-breadcrumb-link a').contains(page.contains).should('be.visible');
    });
  });
});
