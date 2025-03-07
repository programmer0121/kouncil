import {Injectable} from '@angular/core';
import {Observable, Subject} from 'rxjs';

@Injectable()
export class SearchService {

  private phrase = new Subject<string>();
  private currentPhraseStore = {};
  currentPhrase: string;
  currentTabName: string;

  phraseChangeHandle(state: string) {
    this.currentPhrase = state;
    this.currentPhraseStore[this.currentTabName] = state;
    this.phrase.next(state);
  }

  getPhraseState(currentTabName: string): Observable<string> {
    this.currentTabName = currentTabName;
    this.currentPhrase = this.currentPhraseStore[currentTabName] ? this.currentPhraseStore[currentTabName] : '';
    return this.phrase.asObservable();
  }

}
