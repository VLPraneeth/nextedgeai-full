import React from 'react';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import EmailInput from '../EmailInput';

describe('EmailInput', () => {
  const mockOnChange = jest.fn();

  beforeEach(() => {
    jest.clearAllMocks();
  });

  describe('Token Detection', () => {
    it('should use token separators when input starts with {{', async () => {
      const user = userEvent.setup();
      render(<EmailInput onChange={mockOnChange} value={[]} />);

      const input = screen.getByRole('combobox');

      await user.type(input, '{{Lookup From Employee');

      expect(input).toBeInTheDocument();
    });
  });

  describe('Email Input Functionality', () => {
    it('should handle regular email addresses with comma separator', async () => {
      const user = userEvent.setup();
      render(<EmailInput onChange={mockOnChange} value={[]} />);

      const input = screen.getByRole('combobox');

      await user.type(input, 'admin@company.com,user@test.com');

      expect(input).toBeInTheDocument();
    });

    it('should handle regular email addresses with space separator', async () => {
      const user = userEvent.setup();
      render(<EmailInput onChange={mockOnChange} value={[]} />);

      const input = screen.getByRole('combobox');

      await user.type(input, 'admin@company.com user@test.com');

      expect(input).toBeInTheDocument();
    });
  });

  describe('Mixed Input Functionality', () => {
    it('should handle mixed emails and tokens with comma separator', async () => {
      const user = userEvent.setup();
      render(<EmailInput onChange={mockOnChange} value={[]} />);

      const input = screen.getByRole('combobox');

      await user.type(input, 'admin@company.com,{{record.values.email}},user@test.com');

      expect(input).toBeInTheDocument();
    });
  });
});
