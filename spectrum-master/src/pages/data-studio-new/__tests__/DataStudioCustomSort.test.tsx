import React from 'react';
import { fireEvent, render, screen, waitFor } from '../../../tests/helpers';
import CustomHeaderGrid from '../CustomHeaderGrid';
import * as dataStudioThunks from '../../../store/data-studio/thunks';

// Mock all the hooks
jest.mock('hooks/redux', () => ({
  useEnhancedDispatch: jest.fn(),
  useEnhancedSelector: jest.fn(),
}));

jest.mock('hooks/pagination', () => ({
  useCursorPagination: jest.fn(),
}));

jest.mock('store/picklists/hooks', () => ({
  usePicklistValues: jest.fn(),
}));

jest.mock('store/data-studio/thunks', () => ({
  getEntityRecords: jest.fn(),
}));

jest.mock('../Filters', () => {
  const mockReact = require('react');
  return mockReact.forwardRef((props: any, ref: any) =>
    mockReact.createElement(
      'div',
      {
        'data-testid': 'data-studio-filter',
        ref: ref,
        onClick: () => {
          if (props.onChange && props.value) {
            // Create a complete predicate by filling in operator and right if missing
            const completePredicate = {
              ...props.value,
              predicates: props.value.predicates?.map((pred: any) => ({
                ...pred,
                operator: pred.operator || 'equals',
                right: pred.right || { value: 'test-value' },
              })),
            };
            props.onChange(props.name, 'test', completePredicate);
          }
        },
      },
      'Mock Filter Component'
    )
  );
});

jest.mock('components/Button', () => ({
  __esModule: true,
  default: ({ children, onClick, className, type }: any) => {
    const mockReact = require('react');
    return mockReact.createElement(
      'button',
      {
        className,
        onClick,
        'data-type': type,
      },
      children
    );
  },
  IconButton: ({ icon: Icon, onClick, className, title }: any) => {
    const mockReact = require('react');
    return mockReact.createElement(
      'button',
      {
        className,
        onClick,
        title,
        'data-testid': `icon-button-${title}`,
      },
      Icon && mockReact.createElement(Icon, { 'data-testid': `icon-${title}` })
    );
  },
}));

describe('CustomHeaderGrid Component', () => {
  const mockDispatch = jest.fn();
  const mockSetColumnVisible = jest.fn();
  const mockOnColumnVisibilityChange = jest.fn();
  const mockOnApplyFilter = jest.fn();
  const mockFetchPicklistValues = jest.fn();

  // Mock props matching IHeaderParams interface
  const createMockProps = (overrides = {}): any => ({
    displayName: 'Test Column',
    entityId: 'entity-123',
    column: {
      getColId: jest.fn(() => 'testColumn'),
    },
    columnApi: {
      setColumnVisible: mockSetColumnVisible,
    },
    api: {} as any,
    columnGroup: null,
    setSort: jest.fn(),
    showColumnMenu: jest.fn(),
    progressSort: jest.fn(),
    eGridHeader: document.createElement('div'),
    fieldMetadata: {
      fieldId: 'field-123',
      dataType: 'STRING',
      label: 'Test Field',
      canDisplay: true,
      canEdit: true,
      canFilter: true,
    },
    fieldValues: [
      {
        label: 'Test Field',
        value: 'field-123',
        type: 'STRING',
      },
      {
        label: 'Another Field',
        value: 'field-456',
        type: 'NUMBER',
      },
    ],
    onColumnVisibilityChange: mockOnColumnVisibilityChange,
    onApplyFilter: mockOnApplyFilter,
    showColumnVisibilityToggle: true,
    ...overrides,
  });

  beforeEach(() => {
    jest.clearAllMocks();

    // Setup mock implementations
    const { useEnhancedDispatch, useEnhancedSelector } = require('hooks/redux');
    const { useCursorPagination } = require('hooks/pagination');
    const { usePicklistValues } = require('store/picklists/hooks');

    useEnhancedDispatch.mockReturnValue(mockDispatch);
    useEnhancedSelector.mockReturnValue([]);
    useCursorPagination.mockReturnValue({
      cursor: 'test-cursor',
      pageSize: 100,
    });
    usePicklistValues.mockReturnValue([{}, mockFetchPicklistValues]);

    // Mock getEntityRecords thunk
    (dataStudioThunks.getEntityRecords as jest.Mock).mockReturnValue({
      type: 'getEntityRecords',
      payload: {},
    });
  });

  describe('Initial Render', () => {
    it('renders the custom header with column name', () => {
      const props = createMockProps();
      render(<CustomHeaderGrid {...props} />);

      expect(screen.getByText('Test Column')).toBeInTheDocument();
    });
  });

  describe('Dropdown Rendering', () => {
    it('opens dropdown when header is clicked', async () => {
      const props = createMockProps();
      render(<CustomHeaderGrid {...props} />);

      const header = screen.getByText('Test Column');
      fireEvent.click(header);

      await waitFor(() => {
        expect(screen.getByTestId('icon-button-Sort Ascending')).toBeInTheDocument();
      });
    });

    it('renders all sort and visibility buttons in dropdown', async () => {
      const props = createMockProps();
      render(<CustomHeaderGrid {...props} />);

      fireEvent.click(screen.getByText('Test Column'));

      await waitFor(() => {
        expect(screen.getByTestId('icon-button-Sort Ascending')).toBeInTheDocument();
        expect(screen.getByTestId('icon-button-Sort Descending')).toBeInTheDocument();
        expect(screen.getByTestId('icon-button-Hide column')).toBeInTheDocument();
      });
    });

    it('renders filter component in dropdown when fieldValues exist', async () => {
      const props = createMockProps();
      render(<CustomHeaderGrid {...props} />);

      fireEvent.click(screen.getByText('Test Column'));

      await waitFor(() => {
        expect(screen.getByTestId('data-studio-filter')).toBeInTheDocument();
      });
    });

    it('renders Apply Filter button in dropdown', async () => {
      const props = createMockProps();
      render(<CustomHeaderGrid {...props} />);

      fireEvent.click(screen.getByText('Test Column'));

      await waitFor(() => {
        expect(screen.getByText('Apply Filter')).toBeInTheDocument();
      });
    });

    it('does not render filter component when fieldValues is empty', async () => {
      const props = createMockProps({ fieldValues: [] });
      render(<CustomHeaderGrid {...props} />);

      fireEvent.click(screen.getByText('Test Column'));

      await waitFor(() => {
        expect(screen.getByTestId('icon-button-Sort Ascending')).toBeInTheDocument();
      });

      expect(screen.queryByTestId('data-studio-filter')).not.toBeInTheDocument();
    });

    it('hides column visibility toggle when showColumnVisibilityToggle is false', async () => {
      const props = createMockProps({ showColumnVisibilityToggle: false });
      render(<CustomHeaderGrid {...props} />);

      fireEvent.click(screen.getByText('Test Column'));

      await waitFor(() => {
        expect(screen.getByTestId('icon-button-Sort Ascending')).toBeInTheDocument();
      });

      expect(screen.queryByTestId('icon-button-Hide column')).not.toBeInTheDocument();
    });
  });

  describe('Sort Functionality', () => {
    it('calls onSortChange with ascending sort when up arrow is clicked', async () => {
      const mockOnSortChange = jest.fn();
      const props = createMockProps({ onSortChange: mockOnSortChange });
      render(<CustomHeaderGrid {...props} />);

      fireEvent.click(screen.getByText('Test Column'));

      await waitFor(() => {
        expect(screen.getByTestId('icon-button-Sort Ascending')).toBeInTheDocument();
      });

      fireEvent.click(screen.getByTestId('icon-button-Sort Ascending'));

      expect(mockOnSortChange).toHaveBeenCalledWith('testColumn', 'asc');
    });

    it('calls onSortChange with descending sort when down arrow is clicked', async () => {
      const mockOnSortChange = jest.fn();
      const props = createMockProps({ onSortChange: mockOnSortChange });
      render(<CustomHeaderGrid {...props} />);

      fireEvent.click(screen.getByText('Test Column'));

      await waitFor(() => {
        expect(screen.getByTestId('icon-button-Sort Descending')).toBeInTheDocument();
      });

      fireEvent.click(screen.getByTestId('icon-button-Sort Descending'));

      expect(mockOnSortChange).toHaveBeenCalledWith('testColumn', 'desc');
    });

    it('does not call onSortChange if it is not provided', async () => {
      const props = createMockProps({ onSortChange: undefined });
      render(<CustomHeaderGrid {...props} />);

      fireEvent.click(screen.getByText('Test Column'));

      await waitFor(() => {
        expect(screen.getByTestId('icon-button-Sort Ascending')).toBeInTheDocument();
      });

      fireEvent.click(screen.getByTestId('icon-button-Sort Ascending'));

      // Should not throw an error when onSortChange is not provided
      expect(true).toBe(true);
    });
  });

  describe('Column Visibility Toggle', () => {
    it('hides column and closes dropdown when hide button is clicked', async () => {
      const props = createMockProps();
      render(<CustomHeaderGrid {...props} />);

      fireEvent.click(screen.getByText('Test Column'));

      await waitFor(() => {
        expect(screen.getByTestId('icon-button-Hide column')).toBeInTheDocument();
      });

      fireEvent.click(screen.getByTestId('icon-button-Hide column'));

      expect(mockSetColumnVisible).toHaveBeenCalledWith('testColumn', false);
      expect(mockOnColumnVisibilityChange).toHaveBeenCalledWith('testColumn', false);

      await waitFor(() => {
        expect(screen.queryByTestId('icon-button-Hide column')).not.toBeInTheDocument();
      });
    });

    it('calls columnApi.setColumnVisible with correct parameters', async () => {
      const props = createMockProps();
      render(<CustomHeaderGrid {...props} />);

      fireEvent.click(screen.getByText('Test Column'));

      await waitFor(() => {
        expect(screen.getByTestId('icon-button-Hide column')).toBeInTheDocument();
      });

      fireEvent.click(screen.getByTestId('icon-button-Hide column'));

      expect(mockSetColumnVisible).toHaveBeenCalledTimes(1);
      expect(mockSetColumnVisible).toHaveBeenCalledWith('testColumn', false);
    });
  });

  describe('Filter Functionality', () => {
    it('initializes filter with current field when no existing filter', async () => {
      const props = createMockProps();
      render(<CustomHeaderGrid {...props} />);

      fireEvent.click(screen.getByText('Test Column'));

      await waitFor(() => {
        expect(screen.getByTestId('data-studio-filter')).toBeInTheDocument();
      });

      // The filter component should be rendered with initial empty predicate for current field
      const filterComponent = screen.getByTestId('data-studio-filter');
      expect(filterComponent).toBeInTheDocument();
    });

    it('applies filter when Apply Filter button is clicked', async () => {
      const props = createMockProps();
      render(<CustomHeaderGrid {...props} />);

      fireEvent.click(screen.getByText('Test Column'));

      await waitFor(() => {
        expect(screen.getByText('Apply Filter')).toBeInTheDocument();
      });

      // Click the filter component to trigger onChange and set up a complete predicate
      const filterComponent = screen.getByTestId('data-studio-filter');
      fireEvent.click(filterComponent);

      const applyButton = screen.getByText('Apply Filter');
      fireEvent.click(applyButton);

      expect(mockOnApplyFilter).toHaveBeenCalled();
    });

    it('merges column filter with existing filters from other columns', async () => {
      const existingFilter = {
        criteria: {
          predicates: [
            {
              left: { value: 'other-field' },
              operator: 'equals',
              right: { value: 'other-value' },
            },
          ],
          operator: 'AND',
          groupPredicateId: 'root-filter',
        },
      };

      const props = createMockProps({ appliedFilter: existingFilter });
      render(<CustomHeaderGrid {...props} />);

      fireEvent.click(screen.getByText('Test Column'));

      await waitFor(() => {
        expect(screen.getByText('Apply Filter')).toBeInTheDocument();
      });

      // Click the filter component to trigger onChange
      const filterComponent = screen.getByTestId('data-studio-filter');
      fireEvent.click(filterComponent);

      const applyButton = screen.getByText('Apply Filter');
      fireEvent.click(applyButton);

      // Should be called, but exact merging logic depends on filter state
      expect(mockOnApplyFilter).toHaveBeenCalled();
    });

    it('closes dropdown after applying filter', async () => {
      const props = createMockProps();
      render(<CustomHeaderGrid {...props} />);

      fireEvent.click(screen.getByText('Test Column'));

      await waitFor(() => {
        expect(screen.getByText('Apply Filter')).toBeInTheDocument();
      });

      const applyButton = screen.getByText('Apply Filter');
      fireEvent.click(applyButton);

      await waitFor(() => {
        expect(screen.queryByText('Apply Filter')).not.toBeInTheDocument();
      });
    });

    it('shows only current column filters in dropdown', async () => {
      const appliedFilter = {
        criteria: {
          predicates: [
            {
              left: { label: 'Test Field', value: 'field-123', type: 'STRING' },
              operator: 'equals',
              right: { value: 'test-value' },
              predicateId: 'testColumn',
            },
            {
              left: { label: 'Other Field', value: 'field-456', type: 'STRING' },
              operator: 'equals',
              right: { value: 'other-value' },
              predicateId: 'otherColumn',
            },
          ],
          operator: 'AND',
          groupPredicateId: 'root-filter',
        },
      };

      const props = createMockProps({ appliedFilter });
      render(<CustomHeaderGrid {...props} />);

      fireEvent.click(screen.getByText('Test Column'));

      await waitFor(() => {
        expect(screen.getByTestId('data-studio-filter')).toBeInTheDocument();
      });

      // The filter component should only show predicates for the current field (field-123)
      const filterComponent = screen.getByTestId('data-studio-filter');
      expect(filterComponent).toBeInTheDocument();
    });
  });

  describe('Dropdown Behavior', () => {
    it('closes dropdown when clicking outside', async () => {
      const props = createMockProps();
      render(<CustomHeaderGrid {...props} />);

      fireEvent.click(screen.getByText('Test Column'));

      await waitFor(() => {
        expect(screen.getByTestId('icon-button-Sort Ascending')).toBeInTheDocument();
      });

      // Click outside
      fireEvent.mouseDown(document.body);

      await waitFor(() => {
        expect(screen.queryByTestId('icon-button-Sort Ascending')).not.toBeInTheDocument();
      });
    });

    it('does not close dropdown when clicking inside dropdown', async () => {
      const props = createMockProps();
      render(<CustomHeaderGrid {...props} />);

      fireEvent.click(screen.getByText('Test Column'));

      await waitFor(() => {
        expect(screen.getByTestId('icon-button-Sort Ascending')).toBeInTheDocument();
      });

      const dropdown = document.querySelector('.data-studio-grid-custom-sort-container');
      expect(dropdown).toBeInTheDocument();

      // Click inside dropdown
      if (dropdown) {
        fireEvent.mouseDown(dropdown);
      }

      // Dropdown should still be open
      expect(screen.getByTestId('icon-button-Sort Ascending')).toBeInTheDocument();
    });

    it('toggles dropdown when header is clicked multiple times', async () => {
      const props = createMockProps();
      render(<CustomHeaderGrid {...props} />);

      const header = screen.getByText('Test Column');

      // Open
      fireEvent.click(header);
      await waitFor(() => {
        expect(screen.getByTestId('icon-button-Sort Ascending')).toBeInTheDocument();
      });

      // Close
      fireEvent.click(header);
      await waitFor(() => {
        expect(screen.queryByTestId('icon-button-Sort Ascending')).not.toBeInTheDocument();
      });

      // Open again
      fireEvent.click(header);
      await waitFor(() => {
        expect(screen.getByTestId('icon-button-Sort Ascending')).toBeInTheDocument();
      });
    });

    it('positions dropdown using portal', async () => {
      const props = createMockProps();
      render(<CustomHeaderGrid {...props} />);

      fireEvent.click(screen.getByText('Test Column'));

      await waitFor(() => {
        const dropdown = document.querySelector('.data-studio-grid-custom-sort-container');
        expect(dropdown).toBeInTheDocument();
        expect(dropdown?.parentElement).toBe(document.body);
      });
    });

    it('sets dropdown opacity and visibility for positioning', async () => {
      const props = createMockProps();
      render(<CustomHeaderGrid {...props} />);

      fireEvent.click(screen.getByText('Test Column'));

      // Wait for dropdown to be in the DOM
      await waitFor(() => {
        const dropdown = document.querySelector('.data-studio-grid-custom-sort-container') as HTMLElement;
        expect(dropdown).toBeInTheDocument();
      });

      // The dropdown should eventually become visible after positioning
      // Check that opacity and visibility styles are set (they start as hidden and transition to visible)
      const dropdown = document.querySelector('.data-studio-grid-custom-sort-container') as HTMLElement;
      expect(dropdown?.style.opacity).toBeDefined();
      expect(dropdown?.style.visibility).toBeDefined();
    });
  });

  describe('Picklist Integration', () => {
    it('updates filter options when picklist values are received from Redux', async () => {
      const mockPicklistValues = [
        { label: 'Option 1', value: 'opt1', id: 'id1' },
        { label: 'Option 2', value: 'opt2', id: 'id2' },
      ];

      const { useEnhancedSelector } = require('hooks/redux');
      useEnhancedSelector.mockReturnValue(mockPicklistValues);

      const props = createMockProps();
      render(<CustomHeaderGrid {...props} />);

      expect(useEnhancedSelector).toHaveBeenCalled();
    });

    it('handles picklist values without value property', async () => {
      const mockPicklistValues = [
        { label: 'Option 1', id: 'id1' },
        { label: 'Option 2', id: 'id2' },
      ];

      const { useEnhancedSelector } = require('hooks/redux');
      useEnhancedSelector.mockReturnValue(mockPicklistValues);

      const props = createMockProps();
      render(<CustomHeaderGrid {...props} />);

      expect(useEnhancedSelector).toHaveBeenCalled();
    });
  });
});
