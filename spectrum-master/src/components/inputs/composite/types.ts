//
// Copyright (c) 2019-Present Syncari - All rights reserved.
//

export interface CompositeValues<TCompositeValue = CompositeValue> {
  configId: string;
  name: string;
  compositeValues: TCompositeValue;
}

export interface CompositeValue<TValue = string> {
  name: string;
  value: TValue;
}

export interface CompositeValueContainer<TValue = string> {
  repeatId: string;
  newValue: CompositeValue<TValue>;
  updateField: CompositeValue<TValue>;
}
