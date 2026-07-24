/// <reference types="Cypress" />

context('Login', () => {
  it('.fill up login form and login successfully', () => {
    const username = Cypress.env('username');
    const password = Cypress.env('password');
    cy.login(username, password, '/synapses');
    cy.wait(1000);
    cy.get('.ant-breadcrumb-link a')
      .contains(/Synapse Studio/i)
      .should('be.visible');
  });

  it('.fill up invalid username password and get redirected back to login', () => {
    const username = 'adminfail@syncari.com';
    const password = 'adminerror';
    cy.login(username, password, '/login');
    cy.get('.authentication-content .synri-inline-message.error')
      .contains(/Incorrect username or password/i)
      .should('be.visible');
  });
});
