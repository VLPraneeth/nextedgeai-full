/// <reference types="Cypress" />

const pages = [
  {
    url: '/authentication/forgotpassword',
    contains: 'Forgot password',
  },
  {
    url: '/authentication/passwordresetsuccess',
    contains: 'Reset Successfully',
  },
  {
    url: '/invited-user/setpassword/1',
    contains: 'Please set a password to access Syncari',
  },
];

context('Password Pages', () => {
  it('.navigate to multiple pages', () => {
    pages.forEach(page => {
      cy.visit(page.url);
      cy.url().should('include', page.url);
      cy.get('.authentication-title').contains(page.contains).should('be.visible');
    });
  });

  it('.invalid email', () => {
    let url = '/authentication/forgotpassword';
    cy.visit(url);
    cy.url().should('include', url);
    cy.get('.authentication-title').contains('Forgot password').should('be.visible');
    cy.get('input').type('invalid').should('have.value', 'invalid');
    cy.get('.forgot-password-button').click();
    cy.get('.authentication-title').contains('Password Reset Email Sent').should('be.visible');
  });

  it('.invalid invite', () => {
    let url = '/invited-user/setpassword/1';
    cy.visit(url);
    cy.url().should('include', url);
    cy.get('.authentication-title').contains('Please set a password to access Syncari').should('be.visible');
    cy.get('input').eq(0).type('invalid').should('have.value', 'invalid');
    cy.get('input').eq(1).type('invalid').should('have.value', 'invalid');
    cy.get('button').contains('Update').click({ force: true });
    cy.get('div').contains('expired invitation').should('be.visible');
  });

  it('.mismatch password', () => {
    let url = '/invited-user/setpassword/1';
    cy.visit(url);
    cy.url().should('include', url);
    cy.get('.authentication-title').contains('Please set a password to access Syncari').should('be.visible');
    cy.get('input').eq(0).type('mis').should('have.value', 'mis');
    cy.get('input').eq(1).type('match').should('have.value', 'match');
    cy.get('button').contains('Update').click({ force: true });
    cy.get('div').contains('Passwords must match').should('be.visible');
  });
});
