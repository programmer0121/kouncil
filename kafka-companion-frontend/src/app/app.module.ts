import { BrowserModule } from '@angular/platform-browser';
import { NgModule } from '@angular/core';
import { NgxDatatableModule } from '@swimlane/ngx-datatable'

import { AppComponent } from './app.component';
import { HttpClientModule } from "@angular/common/http";
import { TopicComponent } from './topic/topic.component';
import { RoutingModule } from "./routing/routing.module";
import { FormsModule } from "@angular/forms";
import { TopicsComponent } from './topics/topics.component';
import { SendComponent } from './send/send.component';
import { NavbarComponent } from './navbar/navbar.component';
import { SearchService } from "app/search.service";
import { BrokersComponent } from './brokers/brokers.component';
import { AutosizeDirective } from "app/util/autosize.directive";
import { ConsumerGroupsComponent } from "app/consumers/consumer-groups/consumer-groups.component";
import { ConsumerGroupComponent } from "app/consumers/consumer-group/consumer-group.component";
import { ToolbarComponent } from "app/topic/toolbar/toolbar.component";
import { ClipboardModule } from '@angular/cdk/clipboard'
import { NgxJsonViewerModule } from 'ngx-json-viewer';
import { ProgressBarComponent } from "./util/progress-bar.component";
import { NoDataPlaceholderComponent } from './no-data-placeholder/no-data-placeholder.component';
import { TopicPartitionsComponent} from './topic/topic-partitions.component';
import { SendPopupComponent } from './send/send-popup.component';
import {TopicPaginationComponent} from './topic/topic-pagination.component';

@NgModule({
  declarations: [
    AppComponent,
    TopicComponent,
    TopicsComponent,
    ConsumerGroupsComponent,
    SendComponent,
    NavbarComponent,
    ToolbarComponent,
    BrokersComponent,
    AutosizeDirective,
    ConsumerGroupComponent,
    ProgressBarComponent,
    NoDataPlaceholderComponent,
    TopicPartitionsComponent,
    NoDataPlaceholderComponent,
    SendPopupComponent,
    TopicPaginationComponent
  ],
  imports: [
    BrowserModule,
    HttpClientModule,
    RoutingModule,
    FormsModule,
    NgxDatatableModule,
    ClipboardModule,
    NgxJsonViewerModule
  ],
  providers: [SearchService],
  bootstrap: [AppComponent]
})
export class AppModule {
}
