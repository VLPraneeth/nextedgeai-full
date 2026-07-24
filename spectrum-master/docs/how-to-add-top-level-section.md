# How To: Add a Top-Level Section to Syncari

The Syncari app is divided into several top-level sections based on product domains and functionality, such as Synapse Studio, Sync Studio and Data Studio. Each of these sections maps to a directory within `src/pages/` that contain the entry point, layouts and logic specific to that section.

When adding a new top-level section to the app, follow these steps to ensure proper setup:

## 1. Create a new folder under `src/pages/` with the section name

Whenever possible, use the user-facing name for your section folders, files, urls and other code for easier association between the interface code code.

Folder names should use snake-case.

Example: `src/pages/my-new-section/`

## 2. Create the section's root component

Every "page" in a React app requires a single component to render at the specified URL. Create the entry point component and associated files inside the directory you just made. Component and style files should use PascalCase.

```
src/pages/my-new-section/
|-- MyNewSection.less
|-- MyNewSection.test.tsx
|-- MyNewSection.tsx
```

In the root component, set the page title using the `useWindowTitle()` hook and return at least a `<div>Hello World</div>` so you can see some output once the routes are successfully connected.

## 3. Set up the new URL route

URL paths for Syncari pages are stored as constant values in `src/utils/RouteConstants.ts` and referenced where needed to reduce surface area of future updates. Add the path for your new section to the `RouteConstants` object using CONST_CASE for the key.

```
const RouteConstants = {
    ...,
    MY_NEW_SECTION               : '/my-new-section'
}
```

Once added to `RouteConstants`, go to `src/utils/UrlUtil.ts` and add a reference to that value to `ROOT_PATHS` array. This enables the section name to be automatically generated from the URL in `MainHeaderBreadcrumb.tsx`.

```
const {
    ...,
    MY_NEW_SECTION,
} = RouteConstants;

export const ROOT_PATHS = {
    ...,
    MY_NEW_SECTION,
};
```

## 4. Connect the route to the root component

`MainPageLayout.tsx` holds the main router for the application. In this file, lazy load your root component and add it as a direct child of the `<Router>` within the return statement. Our routing package `reach-router` treats all direct children as Route components that render when their `path` prop matches the current URL. Adding a wildcard character will ensure that any future sub-routes will still route to the correct section.

```
// MainPageLayout.tsx

// Lazy load root component outside MainPageLayout
const MyNewSection = EnhancedReactLazy(() => import('pages/my-new-section/MyNewSection'));

const MainPageLayout = () => {
    ...
    return (
        ...
        <Router className="main-page-layout-container">
            ...
            <MyNewSection path={`${RouteConstants.MY_NEW_SECTION}/*`} />
        </Router>
)}
```

To hide/show a route based on a feature flag or user's role, conditionally render the route component using `userCan()` or `isFeatureEnabled()` utilities.

```
// Feature-flag restricted
{isFeatureEnabled(FeatureFlagName.MY_NEW_SECTION)
    && <MyNewSection path={`${RouteConstants.MY_NEW_SECTION}/*`} />}

// Role-restricted
{userCan(arrayOfAllowedRoleNames)
    && <MyNewSection path={`${RouteConstants.MY_NEW_SECTION}/*`} />}
```

## 5. Add the page to the main navigation

If your page needs to be listed in the main sidebar navigation, you'll add a new item to the `MenuItemData` array in `SideNavigationMenu.tsx`, in the same position that you want it to display in the navigation.

To do this, you'll also need:

- An icon
- A section name added to `src/i18n/en-US.json`
- A userflow tag added to `src/utils/UserflowTags.ts`

```
const MenuItemData = [
    ...,
    {
        path: RouteConstants.MY_NEW_SECTION,
        icon: MyNewSectionIcon,
        title: tn('my_new_section'),
        userflowTag: UserflowTags.SideNav.MyNewSection,
    }
```
