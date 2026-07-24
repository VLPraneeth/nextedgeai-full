1. Start off by changing any id to the displayValue you want

   - call your data source that contains the link between your id and display value in `MainHeaderBreadcrumb.tsx`.
     ex:
     `const dashboards = useEnhancedSelector((state) => state.newDashboard.dashboards)`
   - add data source to `lists` variable in `MainHeaderBreadcrumb.tsx`
   - add type of data source to `UrlListMetadata` interface
   - add desired logic to change your display name in `getUrlListItemName`
     - ex:
     - ````case AppConstants.LIST_TYPES.QUICK_START:
               const quickStart = lists?.quickStarts?.find((item) => item.id === id);
               let displayName = quickStart?.displayName;
               return displayName ? displayName : id;```
       ````

2. fix any prefix paths not displaying properly

   - add if statement to `paths.forEach` in `MainHeaderBreadcrumb.tsx`
   - ex:

   ````// Handle Quick Starts
   if (path.includes(AppConstants.LIST_TYPES.QUICK_START)) {
     currentPath = AppConstants.LIST_TYPES.QUICK_START;
   }```

   ````
